/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.serverSide.priority.controllers;

import jetbrains.buildServer.serverSide.BuildTypeComparator;
import jetbrains.buildServer.serverSide.ProjectComparator;
import jetbrains.buildServer.serverSide.SProject;

public class PriorityQueueBuildTypeComparator extends BuildTypeComparator {

  public PriorityQueueBuildTypeComparator() {
    super(new ProjectComparator(true) {
      @Override
      protected int compareOnSameDepth(final SProject o1, final SProject o2) {
        return o1.getName().compareTo(o2.getName());
      }
    }, null);
  }

}
