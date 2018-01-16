#!/usr/bin/env bash

BOARD=stm32f4_disco

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )

source "$DIR/../konan.sh"

mkdir -p $DIR/build && cd $DIR/build

konanc $DIR/src/main.kt -target zephyr -linkerOpts -L/opt/local/Caskroom/gcc-arm-embedded/7-2017-q4-major/gcc-arm-none-eabi-7-2017-q4-major//arm-none-eabi/lib/thumb -linkerOpts -lsupc++ -opt || exit 1

DEP="$HOME/.konan/dependencies"
export ZEPHYR_BASE=/Users/jetbrains/kotlin-native/zephyr/
export ZEPHYR_GCC_VARIANT=gccarmemb
export GCCARMEMB_TOOLCHAIN_PATH=/usr/local/Caskroom/gcc-arm-embedded/7-2017-q4-major/gcc-arm-none-eabi-7-2017-q4-major

[ -f CMakeCache.txt ] || cmake -DCMAKE_VERBOSE_MAKEFILE=ON -DBOARD=$BOARD ..
make 
