class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.2.0"
  
  url "https://github.com/wlezzar/doks/releases/download/0.2.0/doks.zip"
  sha256 "8e5393a4adfaa700ce1289c967737571fafebcfa7747e9ceaeb9b3370ccbe8bb"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
