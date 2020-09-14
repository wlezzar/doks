package com.github.wlezzar.doks.sources

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.DocumentSource
import com.github.wlezzar.doks.DocumentType
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.docs.v1.model.ParagraphElement
import com.google.api.services.docs.v1.model.StructuralElement
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import com.google.api.services.docs.v1.model.Document as GoogleDoc

/**
 * Fetches documents from google drive.
 */
class GoogleDocs(private val gSuite: GSuite, private val folders: List<String>) : DocumentSource {
    override fun fetch(): Flow<Document> = folders.map { gSuite.fetchDocsAsFlow(folder = it) }.merge()
}

private val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
private const val tokensDirectoryPath = "/tmp/gsuite/tokens"

/**
 * Creates an authorized Credential object.
 * @param transport The network HTTP Transport.
 * @return An authorized Credential object.
 * @throws IOException If the credentials.json file cannot be found.
 */
private fun authenticate(
    secret: File,
    userId: String,
    scopes: List<String>,
    transport: NetHttpTransport
): Credential? {
    val clientSecrets =
        secret.inputStream().use { inp -> GoogleClientSecrets.load(jsonFactory, InputStreamReader(inp)) }

    // Build flow and trigger user authorization request.
    val flow =
        GoogleAuthorizationCodeFlow
            .Builder(transport, jsonFactory, clientSecrets, scopes)
            .setDataStoreFactory(FileDataStoreFactory(File(tokensDirectoryPath)))
            .setAccessType("offline")
            .build()

    val receiver = LocalServerReceiver.Builder().setPort(8888).build()
    return AuthorizationCodeInstalledApp(flow, receiver).authorize(userId)
}

/**
 * Returns the text in the given ParagraphElement.
 *
 * @param element a ParagraphElement from a Google Doc
 */
private fun readParagraphElement(element: ParagraphElement): String = element.textRun?.content ?: ""

/**
 * Recurses through a list of Structural Elements to read a document's text where text may be in
 * nested elements.
 *
 * @param elements a list of Structural Elements
 */
private fun readStructuralElements(elements: List<StructuralElement>): String = buildString {
    for (element in elements) {
        when {
            element.paragraph != null -> {
                for (paragraphElement in element.paragraph.elements) {
                    append(readParagraphElement(paragraphElement))
                }
            }
            element.table != null -> {
                // The text in table cells are in nested Structural Elements and tables may be
                // nested.
                for (row in element.table.tableRows) {
                    for (cell in row.tableCells) {
                        append(readStructuralElements(cell.content))
                    }
                }
            }
            element.tableOfContents != null -> {
                // The text in the TOC is also in a Structural Element.
                append(readStructuralElements(element.tableOfContents.content))
            }
        }
    }
}

private fun GoogleDoc.readContent(): String = readStructuralElements(body.content)

class GSuite(val docs: Docs, val drive: Drive) {

    constructor(
        secretFile: File,
        userId: String,
        transport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()
    ) : this(
        drive = Drive
            .Builder(
                transport,
                JacksonFactory.getDefaultInstance(),
                authenticate(
                    secret = secretFile,
                    userId = "${userId}-drive",
                    scopes = listOf(DriveScopes.DRIVE_METADATA_READONLY),
                    transport = transport
                )
            )
            .setApplicationName("Documentation indexer")
            .build(),
        docs = Docs
            .Builder(
                transport,
                JacksonFactory.getDefaultInstance(),
                authenticate(
                    secret = secretFile,
                    userId = "${userId}-docs",
                    scopes = listOf(DocsScopes.DOCUMENTS_READONLY),
                    transport = transport
                )
            )
            .setApplicationName("Documentation indexer")
            .build()
    )
}

private fun GSuite.fetchDocs(folder: String?): Sequence<Document> = sequence {
    var nextPageToken: String? = null
    do {
        val result =
            drive
                .files()
                .list()
                .apply {
                    q =
                        listOfNotNull(
                            "mimeType = 'application/vnd.google-apps.document'",
                            folder?.let { "'$it' in parents" },
                        ).joinToString(separator = " and ")

                    pageSize = 10
                    pageToken = nextPageToken
                }
                .execute()

        nextPageToken = result.nextPageToken

        result.files
            ?.takeIf { it.isNotEmpty() }
            ?.mapNotNull {
                when (it.mimeType) {
                    "application/vnd.google-apps.document" -> {
                        val doc = docs.documents().get(it.id).execute()
                        Document(
                            id = doc.documentId,
                            type = DocumentType.GoogleDoc,
                            title = doc.title,
                            link = "https://docs.google.com/document/d/${doc.documentId}",
                            content = doc.readContent()
                        )
                    }
                    else -> {
                        // TODO: log unknown type
                        null
                    }
                }
            }
            ?.forEach { yield(it) }

    } while (nextPageToken != null)
}

fun GSuite.fetchDocsAsFlow(folder: String?): Flow<Document> = channelFlow {
    launch(Dispatchers.IO) { fetchDocs(folder).forEach { sendBlocking(it) } }
}
