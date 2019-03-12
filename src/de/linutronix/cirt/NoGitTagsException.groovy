#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2019 Linutronix GmbH
/*
 * CI-RT library: No git tags are available exception
 */

package de.linutronix.cirt;

class NoGitTagsException extends RuntimeException {
	NoGitTagsException (String repobranch) {
		super("Missing git tags in repository\n\n"+
		      "The following repository does not contain a tag in the first 1000 commits, as expected:\n"+
		      "${repobranch}");
	}
}
