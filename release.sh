#!/usr/bin/env bash
set -e # Any subsequent(*) commands which fail will cause the shell script to exit immediately

sbt ++2.13.1 -Dquill.macro.log=false -Dquill.scala.version=2.13.1 release
sbt ++2.12.10 -Dquill.macro.log=false -Dquill.scala.version=2.12.10 release
