

package jetbrains.buildServer.serverSide.priority.controllers;

import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import org.jetbrains.annotations.NotNull;

public class Util {

  static Set<String> getBuildTypeIds(@NotNull PriorityClass pc) {
    Set<String> btIds = new HashSet<String>();
    for (SBuildType bt : pc.getBuildTypes()) {
      btIds.add(bt.getBuildTypeId());
    }
    return btIds;
  }

}