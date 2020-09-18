class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.5.0"
  
  url "https://github.com/wlezzar/doks/releases/download/0.5.0/doks.zip"
  sha256 "4d42042a549eea586ac6d56f9e2dfd564d3e76366879fe477fb86429407466e6"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
