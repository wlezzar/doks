class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.3.0"
  
  url "https://github.com/wlezzar/doks/releases/download/0.3.0/doks.zip"
  sha256 "2e54b7c4a94f75f08dccb20b8b62cda92306541760d235e4b0a33c58c8ff53f3"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
