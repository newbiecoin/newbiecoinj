mkdir build
cd createjar
sh packager.sh
sh makerelease.sh
cd ../
cp createjar/release.zip build/Newbiecoin-newest.zip
cp createjar/update.zip build/update.zip
