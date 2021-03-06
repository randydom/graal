/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted;

import java.util.Map;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JavaLangSubstitutions.ClassLoaderSupport;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;

@AutomaticFeature
public class ClassLoaderFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ClassLoaderSupport.class, new ClassLoaderSupport());
    }

    private void createClassLoaders(ClassLoader classLoader) {
        Map<ClassLoader, Target_java_lang_ClassLoader> classLoaders = ImageSingletons.lookup(ClassLoaderSupport.class).classloaders;
        if (!classLoaders.containsKey(classLoader)) {
            ClassLoader parent = classLoader.getParent();
            if (parent != null) {
                createClassLoaders(parent);
                classLoaders.put(classLoader, new Target_java_lang_ClassLoader(classLoaders.get(parent)));
            } else {
                classLoaders.put(classLoader, new Target_java_lang_ClassLoader());
            }
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(object -> {
            if (object instanceof ClassLoader) {
                createClassLoaders((ClassLoader) object);
                return ImageSingletons.lookup(ClassLoaderSupport.class).classloaders.get(object);
            }
            return object;
        });
    }
}
