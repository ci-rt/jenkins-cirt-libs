#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2018 Linutronix GmbH

from junit_xml import TestSuite, TestCase
import sys
from os.path import join
from optparse import OptionParser

oparser = OptionParser()
oparser.add_option("--failure", action="store_true",
                   dest="failure", default=False)
oparser.add_option("--skip", action="store_true",
                   dest="skip", default=False)

(opt, args) = oparser.parse_args(sys.argv)

if len(args) != 3:
  print("wrong number of arguments", file=sys.stderr)
  sys.exit(20)

boottest = args[1]
boottest_dir = args[2]
failure = opt.failure
skip = opt.skip

if skip and failure:
  print("either specify skip or failure", file=sys.stderr)
  sys.exit(20)

result_dir = join(boottest_dir, "boottest")

bootlog = join(result_dir, "boot.log")
cmdline_file = join(result_dir, "cmdline")

case = TestCase(boottest)
case.classname = "boottest"
ts = TestSuite("suite")

if failure:
    case.add_failure_info("failure", None)

if skip:
    case.add_skipped_info("online/offline exception", None)
else:
    with open(bootlog, 'r') as fd:
        system_out = fd.read()
    with open(cmdline_file, 'r') as fd:
        cmdline = fd.read()

    ts.properties = {}
    ts.properties['cmdline'] = cmdline

    case.stdout = system_out

ts.test_cases.append(case)

with open(join(result_dir, 'pyjutest.xml'), 'w') as f:
    TestSuite.to_file(f, [ts], prettyprint=True)
