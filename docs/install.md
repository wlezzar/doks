# Install

## Using brew (MacOs & Linux)

Requires a JDK 11 or higher.

```bash
brew tap wlezzar/doks https://github.com/wlezzar/doks
brew install wlezzar/doks/doks

# use doks
doks --help
```

## Using docker (All platforms)

```bash
docker run -i --rm wlezzar/doks:latest --help
```

To have a native like CLI experience, you can create an alias in your `~/.bashrc` or `~/.zshrc`:

```bash
alias doks='docker run -i --rm -v /tmp/.doks:/root/.doks wlezzar/doks:latest'

doks --help
```

## Manual Tarball install (All platforms)

Requires a JDK 11 or higher.

```bash
# Get the latest release version
DOKS_VERSION=$(curl -s https://api.github.com/repos/wlezzar/doks/releases/latest | jq -r .tag_name)

# Download the tar (or zip) package and unarchive it somewhere in your host (ex. /opt)
wget -O /tmp/doks.zip https://github.com/wlezzar/doks/releases/download/${DOKS_VERSION}/doks.zip
unzip /tmp/doks.zip -d /opt

# Add doks in your path (add the following line to your ~/.bashrc or ~/.zshrc to make it permanent)
export PATH="${PATH}:/opt/doks/bin"

# Check that this is working
doks --help
```
