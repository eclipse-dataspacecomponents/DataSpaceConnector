/*
 *  Copyright (c) 2020-2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering - refactored
 *
 */
package org.eclipse.dataspaceconnector.spi.command;

/**
 * Links together a Command and its handler class
 */
public interface CommandHandlerRegistry<C extends Command> {
    <T extends C> void register(CommandHandler<T> handlerClass);
    
    <T extends C> CommandHandler<T> get(Class<T> commandClass);
}
