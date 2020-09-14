class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.1.0"
  
  url "https://github.com/wlezzar/doks/releases/download/0.1.0/jsql.zip"
  sha256 ""

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
