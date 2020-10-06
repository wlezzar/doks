# Google drive

Scans a Google drive tree to retrieve documents to index. Currently, only Google Docs and Google Slides are supported. Other document types are ignored.

## Configuration

```yaml
sources:
    ## Google drive documents source: scans google drive and uses scans google docs.
    - id: my-drive
      source: googleDrive
      secretFile: /path/to/secret  # path to the json secret file of the oauth2 client to use
      searchQuery: ""  # (optional) search query to use (cf. https://developers.google.com/drive/api/v3/search-files)
      driveId: ""  # (optional) select the drive id to scan (by default: search into "my-drive")
      folders: [""]  # (optional) ids of the folders from which to scan for documents. By default: scans from the root
      concurrency: 4  # (optional) choose the concurrency of the indexing
```

## Supported document types

Currently, only Google Docs and Google Slides are supported. Other documents are ignored. More document types will be supported in the future.