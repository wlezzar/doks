class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.9.1"
  
  url "https://github.com/wlezzar/doks/releases/download/0.9.1/doks.zip"
  sha256 "4be81801e68adaf7dfff28d33f1608d56c96beec8d8975299c1b5c7f8c3149ce"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
