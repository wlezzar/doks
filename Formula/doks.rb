class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.8.5"
  
  url "https://github.com/wlezzar/doks/releases/download/0.8.5/doks.zip"
  sha256 "649dcda12e4368db8962a2233587d27c0f06d99a96e18da9c564b775eaccca12"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
