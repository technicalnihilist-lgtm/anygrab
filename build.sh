#!/bin/bash
set -eo pipefail
export JAVA_HOME=/scratch/work/dl/jdk-17.0.21+9
export PATH=$JAVA_HOME/bin:$PATH
SDK=/scratch/work/android-sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-35/android.jar
SRC=app/src/main
OUT=build
rm -rf $OUT
mkdir -p $OUT/gen $OUT/classes $OUT/dex

echo "==> aapt2 compile"
$BT/aapt2 compile --dir $SRC/res -o $OUT/res.zip

echo "==> aapt2 link"
$BT/aapt2 link -o $OUT/base.apk \
  -I $AJAR \
  --manifest $SRC/AndroidManifest.xml \
  --java $OUT/gen \
  --min-sdk-version 21 --target-sdk-version 35 \
  --version-code 3 --version-name 1.2.0 \
  --auto-add-overlay \
  $OUT/res.zip

echo "==> javac"
find $OUT/gen -name "*.java" > $OUT/sources.txt
find $SRC/java -name "*.java" >> $OUT/sources.txt
javac -source 8 -target 8 -nowarn -bootclasspath $AJAR -d $OUT/classes @$OUT/sources.txt 2>&1 | grep -v "^Note:" || { echo "JAVAC FAILED"; exit 1; }

echo "==> d8"
find $OUT/classes -name "*.class" > $OUT/classes.txt
$BT/d8 -JXmx640m --min-api 21 --lib $AJAR --output $OUT/dex @$OUT/classes.txt

echo "==> package"
cp $OUT/base.apk $OUT/unsigned.apk
cd $OUT/dex && zip -q ../unsigned.apk classes.dex && cd ../..

echo "==> zipalign"
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "==> sign"
if [ ! -f keystore.jks ]; then
  keytool -genkeypair -v -keystore keystore.jks -alias anygrab \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass anygrab123 -keypass anygrab123 \
    -dname "CN=AnyGrab, OU=Mobile, O=AnyGrab, L=Mumbai, ST=Maharashtra, C=IN" >/dev/null 2>&1
fi
$BT/apksigner -JXmx384m sign --ks keystore.jks --ks-pass pass:anygrab123 --key-pass pass:anygrab123 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out AnyGrab-1.2.0.apk $OUT/aligned.apk

echo "==> verify"
$BT/apksigner -JXmx384m verify --print-certs AnyGrab-1.2.0.apk | head -5
ls -la AnyGrab-1.2.0.apk
