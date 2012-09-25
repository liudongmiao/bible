#!/bin/bash

# the drawable xml and png are stolen from weixin42android.

BIBLE_APP=$PWD
pushd ~/Downloads/weixin42android/res/

cp --parents -v drawable*/webview*back* $BIBLE_APP/res/
cp --parents -v drawable*/webview*forward* $BIBLE_APP/res/
cp --parents -v drawable*/webview*refresh* $BIBLE_APP/res/
cp --parents -v drawable*/*mm_title_btn_right*.xml drawable*/mm_title_btn_[a-z]*\.9.png $BIBLE_APP/res/
cp --parents -v drawable*/webviewtab_bg\.* $BIBLE_APP/res/
cp --parents -v drawable*/mmtitle_bg\.* $BIBLE_APP/res/

popd
