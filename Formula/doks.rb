class Doks < Formula
  desc "Provides a simple search engine over your distributed documentation"
  homepage "https://github.com/wlezzar/doks"
  bottle :unneeded
  version "0.6.0"
  
  url "https://github.com/wlezzar/doks/releases/download/0.6.0/doks.zip"
  sha256 "3f4912a0c867ad4259b3331b5be9c905e1c2ab34f33c1f07d5ba7bdaf54709b6"

  def install
    bin.install "bin/doks"
    prefix.install "lib"
  end
end
