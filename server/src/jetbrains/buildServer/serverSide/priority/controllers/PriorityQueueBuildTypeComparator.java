

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