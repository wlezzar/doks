class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.9.0"
  
  url "https://github.com/wlezzar/doks/releases/download/0.9.0/doks.zip"
  sha256 "af07f7446acc514a3725b28133c113953b64b023328a41bf2efec1f14c3ce336"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
