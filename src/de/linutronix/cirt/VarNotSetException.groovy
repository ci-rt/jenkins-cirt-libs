#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT library: Variable not set exception
 */

package de.linutronix.cirt;

class VarNotSetException extends RuntimeException {
        VarNotSetException (String name) {
                super("Invalid test-description\n\n"+
		      "Please check your test description and set all mandatory environment variables\n"+
		      "(some variables are created when parsing the test-description):\n\n"+
		      "variable ${name} is not set");
        }
}
