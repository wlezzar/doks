package com.github.wlezzar.doks.sources

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.DocumentSource
import com.github.wlezzar.doks.utils.retryable
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.time.Duration
import com.google.api.services.drive.model.File as DriveFile

private val logger = LoggerFactory.getLogger(GoogleDocs::class.java)

/**
 * Fetches documents from google drive.
 */
class GoogleDocs(
    private val sourceId: String,
    secretFile: File,
    private val searchQuery: String?,
    private val driveId: String?,
    private val folders: List<String>?,
    private val concurrency: Int = 4
) : DocumentSource {

    private val tokensDirectoryPath = "/tmp/gsuite/tokens"
    private val transport = GoogleNetHttpTransport.newTrustedTransport()
    private val drive =
        Drive
            .Builder(
                transport,
                JacksonFactory.getDefaultInstance(),
                authenticate(
                    secret = secretFile,
                    userId = "doks-drive6",
                    tokensDirectoryPath = tokensDirectoryPath,
                    scopes = listOf(DriveScopes.DRIVE_READONLY),
                    transport = transport,
                )
            )
            .setApplicationName("Doks")
            .build()

    override fun fetch(): Flow<Document> = channelFlow {
        val files =
            drive
                .listFiles(driveId, searchQuery, folders, "files(id, name, mimeType, webViewLink)")
                .produceIn(this)

        repeat(times = concurrency) {
            launch {
                for (file in files) {
                    when (file.mimeType) {

                        in setOf("application/vnd.google-apps.document", "application/vnd.google-apps.presentation") ->
                            send(
                                Document(
                                    id = file.id,
                                    title = file.name,
                                    source = sourceId,
                                    link = file.webViewLink,
                                    content = drive.export(file, "text/plain"),
                                    metadata = mapOf("mimeType" to file.mimeType)
                                )
                            )

                        else -> {
                            logger.info("ignoring file: ${file.name} (type: ${file.mimeType})")
                        }
                    }
                }
            }
        }
    }
}

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
    tokensDirectoryPath: String,
    transport: NetHttpTransport,
    jsonFactory: JacksonFactory = JacksonFactory.getDefaultInstance()
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

private fun Drive.listFiles(driveId: String?, query: String?, folders: List<String>?, fields: String?) = channelFlow {
    var nextPageToken: String? = null
    do {
        @Suppress("BlockingMethodInNonBlockingContext")
        val result = withContext(Dispatchers.IO) {
            files()
                .list()
                .apply {
                    q = listOfNotNull(
                        query?.let { "($it)" },
                        folders?.joinToString(separator = " or ", prefix = "(", postfix = ")") { "'$it' in parents" },
                    ).joinToString(separator = " and ")

                    driveId?.let { this.driveId = it }

                    this.fields = listOfNotNull("nextPageToken", fields).joinToString(separator = ", ")
                    this.pageSize = 100
                    this.pageToken = nextPageToken
                }
                .execute()
        }

        nextPageToken = result.nextPageToken

        result.files?.forEach {
            logger.debug("found: ${it.name} (id: ${it.id})")
            send(it)
        }

    } while (nextPageToken != null)
}

@Suppress("BlockingMethodInNonBlockingContext")
private suspend fun Drive.export(file: DriveFile, mimeType: String): String = retryable(
    delayBetweenRetries = Duration.ofSeconds(3),
    maxRetries = 10,
    retryOn = { err ->
        err is GoogleJsonResponseException && err.details?.errors?.any { it.reason == "userRateLimitExceeded" } == true
    },
    messageOnError = { _ -> "Drive rate limit exceeded" }
) {
    withContext(Dispatchers.IO) {
        logger.debug("downloading content of file '${file.id}' as '$mimeType'")
        files()
            .export(file.id, mimeType)
            .executeMediaAsInputStream()
            .readBytes()
            .toString(charset = Charsets.UTF_8)
    }
}