# Github

The github source clones a list of repository and scans the documents to index.

## Configuration

```yaml
sources:

  - id: my_repos  # id of the source (may be used for filtering during search 'kafka aws and source:my_repos'
    source: github
    ## (optional) select the transport mode to use (ssh or http). Default is ssh.
    transport: http
    ## By default, all markdown and text files are indexed (.md && .txt). You can choose what files to include instead
    ## by using the include clause
    include:
      - '^.*\.txt$'
    ## (optional) You can also exclude files
    exclude:
      - '^.*\.hello.txt$'
    ## (optional) You can choose the concurrency of the repository cloning and the indexing part
    concurrency: 4
    ## List of repositories to clone and index
    repositories:
      ## use `from: list` to use a static list of repositories to index. See below for other examples of `from` clauses.
      from: list
      list:
        - name: wlezzar/jtab
        - name: wlezzar/doks
          # you can also override the includes / excludes at the repository level
          include: ['^.*\.txt$']
          exclude: ['^.*\.hello$']
        - name: adevinta/zoe
```

## Fetch repositories using a static list

```yaml
sources:

  - id: my_repos  # id of the source (may be used for filtering during search 'kafka aws and source:my_repos'
    source: github
    repositories:
      ## use `from: list` to reference a static list of repositories to index.
      from: list
      ## (optional) Remote Git server. Default is: github.com
      server: "github.my-company.com"
      ## List of repositories to clone
      list:
        - name: wlezzar/jtab  # Name of the repository to clone
          folder: docs  # (optional) Base folder from where to start the file scan. By default: scans from the root
          branch: "mybranch"  # (optional) Branch to checkout. Default: "master"
          include: [""]  # (optional) you can override the includes at the repository level
          exclude: [""]  # (optional) you can override the excludes at the repository level
        - name: adevinta/zoe
```

## Fetch repositories using the Github search API

```yaml
sources:

  - id: my_repos  # id of the source (may be used for filtering during search 'kafka aws and source:my_repos'
    source: github
  
    ## This section defines the target git repositories to clone
    repositories:
      ## use `from: api` to perform a search query on the api to find the repositories to clone
      from: api
      ## (optional) you can select the endpoint you want to use (by default: api.github.com)
      endpoint: https://github.my-company.com/api/v3
      ## (optional) use a file containing the oauth client token to authenticate to the API 
      tokenFile: /home/wlezzar/secrets/gh-enterprise-token-read-only.txt
      ## (optional) enter a search query to retrieve the repositories (cf. https://docs.github.com/en/github/searching-for-information-on-github/searching-for-repositories) 
      search: "serenity/serenity in:name"
      ## (optional) include repositories starred by a list of users
      starredBy: 
        - wlezzar
```
