/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.Export;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;

@StaticInit
public final class ExceptionHandling {
    private ExceptionHandling() {
    }

    public static native CallSite findCallSiteById(int id, Address frame);

    public static void printStack() {
        Address stackFrame = ShadowStack.getStackTop();
        while (stackFrame != null) {
            int callSiteId = ShadowStack.getCallSiteId(stackFrame);
            CallSite callSite = findCallSiteById(callSiteId, stackFrame);
            CallSiteLocation location = callSite.location;

            Console.printString("    at ");
            if (location.className == null || location.methodName == null) {
                Console.printString("(Unknown method)");
            } else {
                Console.printString(location.className.value);
                Console.printString(".");
                Console.printString(location.methodName.value);
            }
            Console.printString("(");
            if (location.fileName != null && location.lineNumber >= 0) {
                Console.printString(location.fileName.value);
                Console.printString(":");
                Console.printInt(location.lineNumber);
            }
            Console.printString(")\n");
            stackFrame = ShadowStack.getNextStackFrame(stackFrame);
        }
    }

    private static Throwable thrownException;

    @Export(name = "sys_catchException")
    @Unmanaged
    public static Throwable catchException() {
        Throwable exception = thrownException;
        thrownException = null;
        return exception;
    }

    @Unmanaged
    public static void throwException(Throwable exception) {
        thrownException = exception;

        RuntimeObject exceptionPtr = Address.ofObject(exception).toStructure();
        RuntimeClass exceptionClass = RuntimeClass.getClass(exceptionPtr);

        Address stackFrame = ShadowStack.getStackTop();
        stackLoop: while (stackFrame != null) {
            int callSiteId = ShadowStack.getCallSiteId(stackFrame);
            CallSite callSite = findCallSiteById(callSiteId, stackFrame);
            ExceptionHandler handler = callSite.firstHandler;

            for (int i = 0; i < callSite.handlerCount; ++i) {
                if (handler.exceptionClass == null || handler.exceptionClass.isSupertypeOf.apply(exceptionClass)) {
                    ShadowStack.setExceptionHandlerId(stackFrame, handler.id);
                    break stackLoop;
                }

                handler = Structure.add(ExceptionHandler.class, handler, 1);
            }

            ShadowStack.setExceptionHandlerId(stackFrame, callSiteId - 1);
            stackFrame = ShadowStack.getNextStackFrame(stackFrame);
        }
    }

    @Unmanaged
    public static void throwClassCastException() {
        throw new ClassCastException();
    }

    @Unmanaged
    public static void throwNullPointerException() {
        throw new NullPointerException();
    }

    @Unmanaged
    public static int callStackSize() {
        Address stackFrame = ShadowStack.getStackTop();
        int size = 0;
        while (stackFrame != null) {
            stackFrame = ShadowStack.getNextStackFrame(stackFrame);
            size++;
        }
        return size + 1;
    }

    @Unmanaged
    public static void fillStackTrace(StackTraceElement[] target) {
        Address stackFrame = ShadowStack.getStackTop();
        int index = 0;
        while (stackFrame != null && index < target.length) {
            int callSiteId = ShadowStack.getCallSiteId(stackFrame);
            CallSite callSite = findCallSiteById(callSiteId, stackFrame);
            CallSiteLocation location = callSite.location;
            StackTraceElement element = createElement(
                    location != null && location.className != null ? location.className.value : "",
                    location != null && location.methodName != null ? location.methodName.value : "",
                    location != null && location.fileName != null ? location.fileName.value : null,
                    location != null ? location.lineNumber : -1);
            target[index++] = element;
            stackFrame = ShadowStack.getNextStackFrame(stackFrame);
        }
    }

    private static StackTraceElement createElement(String className, String methodName, String fileName,
            int lineNumber) {
        return new StackTraceElement(className, methodName, fileName, lineNumber);
    }
}
