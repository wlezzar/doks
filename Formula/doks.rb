class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.4.0"
  
  url "https://github.com/wlezzar/doks/releases/download/0.4.0/doks.zip"
  sha256 "b7dc9629bba7fd952ba9af9f830a19efd53cabc415e55456710680c48d14bdbe"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
