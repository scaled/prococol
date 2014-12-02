#!/bin/sh
#
# Builds and tests (for travis-ci.org)

wget https://raw.githubusercontent.com/scaled/pacman/master/bin/build-test.sh
sh build-test.sh git:https://github.com/scaled/prococol.git
rm build-test.sh
