class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  version "0.9.3"
  
  url "https://github.com/wlezzar/doks/releases/download/0.9.3/doks.zip"
  sha256 "028c3ef132ffcb8c825b14d4a4286fe93c93b9fd738d4577be796b3e9a8dc2ee"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
