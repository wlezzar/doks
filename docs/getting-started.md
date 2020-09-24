# Getting started

The document walks you through how to set up doks locally to perform search queries over markdown documents coming from github.

If you haven't installed doks yet, check out the [installation procedure](install.md).

## Prepare the configuration

First step is to create a configuration file for doks. Doks expect a configuration directory to be present at `$HOME/.doks/config`. Let's create a simple configuration file there:

```bash
mkdir -p $HOME/.doks/config

cat > $HOME/.doks/config/default.yml <<EOF
# sources to fetch documents from
sources:
  - id: github
    source: github 
    repositories:
      from: list
      list:
        - name: wlezzar/jtab
        - name: wlezzar/doks
        - name: adevinta/zoe
    include:
      - '^.*\.md$'

# Search engine to use
engine:
  use: lucene
EOF
```

The above commands create a configuration file that references a static list of github repositories and include only markdown files in the index (cf. the `include` clause). It also uses Lucene as the search engine for a minimal setup (elasticsearch could have been used instead).

## Trigger the indexing
 
Next step is to trigger the indexing process:


```bash
❯ doks index
                                 
14:23:58 INFO  - [wlezzar/jtab] cloning into '/tmp/doks1910419821411559250'
14:24:00 INFO  - [wlezzar/jtab] 1 documents found!
14:24:00 INFO  - [wlezzar/doks] cloning into '/tmp/doks8654245137262434689'
14:24:02 INFO  - [wlezzar/doks] 0 documents found!
14:24:02 INFO  - [adevinta/zoe] cloning into '/tmp/doks756581970042346717'
14:24:04 INFO  - [adevinta/zoe] 36 documents found!
```

## Start searching

You can perform a search query directly using the CLI as the following:

```bash
❯ doks search 'aws kafka' | jq '.'
```

Or you can spin up the Doks UI for a better experience:

```
❯ doks serve
23:16:10 INFO  - listening on port: 8888
```

The UI should be available at `localhost:8888`:

![Main page](img/main-page.png)