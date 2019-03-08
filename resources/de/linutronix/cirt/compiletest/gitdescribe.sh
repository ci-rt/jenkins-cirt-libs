#!/bin/bash
# SPDX-License-Identifier: MIT
# Copyright (c) 2018 Linutronix GmbH

# Create information about git tags and description
#
# Exit status 0 on success
# Exit status 1 when 'git describe' was not successful
#
# The variables 'GIT_TAG' and 'GIT_NAME' are printed to stdout

DEPTH=1000

DESCR="$(git describe HEAD)"

# When "git describe HEAD" does not work, larger history depth is required; We
# do not make this in a loop - we define that there must be a tag in the last
# $DEPTH commits.
if [ x = x"$DESCR" ]
then
	git fetch --depth $DEPTH
	DESCR="$(git describe HEAD)"
fi

if [ x != x"$DESCR" ]
then
	echo "TAGS_COMMIT=$(git rev-parse HEAD)"
	echo "TAGS_NAME=$DESCR"

	exit 0
else
	# There was no tag in the last $DEPTH commits, return with an error.
	exit 1
fi
