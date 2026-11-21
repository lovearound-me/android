#!/bin/sh
# The widget picker's and the tile list's previews, drawn by the tests
# (WidgetScreensTest, TileScreensTest) and copied into res: English is the
# default, Russian in Russian.
#
#     scripts/widget-previews.sh
set -e
cd "$(dirname "$0")/.."
./gradlew -q :app:testDebugUnitTest --tests '*WidgetScreensTest*' :wear:testDebugUnitTest --tests '*TileScreensTest*' -Proborazzi.test.record=true
for lang in en ru; do
  case $lang in en) q="";; *) q="-$lang";; esac
  mkdir -p app/src/main/res/drawable$q-nodpi wear/src/main/res/drawable$q-nodpi
  cp app/build/outputs/roborazzi/widgets/$lang/person.png app/src/main/res/drawable$q-nodpi/widget_person_preview.png
  cp app/build/outputs/roborazzi/widgets/$lang/close-row.png app/src/main/res/drawable$q-nodpi/widget_close_preview.png
  cp wear/build/outputs/roborazzi/tile/$lang/people.png wear/src/main/res/drawable$q-nodpi/tile_preview.png
done
echo "previews copied"
