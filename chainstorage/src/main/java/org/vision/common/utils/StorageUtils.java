package org.vision.common.utils;

import static org.vision.common.parameter.CommonParameter.ENTROPY_LIMIT_HARD_FORK;
import static org.vision.common.utils.DbOptionalsUtils.createDefaultDbOptions;

import java.io.File;
import org.apache.commons.lang3.StringUtils;
import org.iq80.leveldb.Options;
import org.vision.common.parameter.CommonParameter;


public class StorageUtils {

  public static boolean getEntropyLimitHardFork() {
    return ENTROPY_LIMIT_HARD_FORK;
  }

  public static String getOutputDirectoryByDbName(String dbName) {
    String path = getPathByDbName(dbName);
    if (!StringUtils.isBlank(path)) {
      return path;
    }
    return getOutputDirectory();
  }

  public static String getPathByDbName(String dbName) {
    if (hasProperty(dbName)) {
      return getProperty(dbName).getPath();
    }
    return null;
  }

  private static boolean hasProperty(String dbName) {
    if (CommonParameter.getInstance().getStorage()
        .getPropertyMap() != null) {
      return CommonParameter.getInstance().getStorage()
          .getPropertyMap().containsKey(dbName);
    }
    return false;
  }

  private static Property getProperty(String dbName) {
    return CommonParameter.getInstance().getStorage()
        .getPropertyMap().get(dbName);
  }

  public static String getOutputDirectory() {
    if (!"".equals(CommonParameter.getInstance().getOutputDirectory())
        && !CommonParameter.getInstance().getOutputDirectory().endsWith(File.separator)) {
      return CommonParameter.getInstance().getOutputDirectory() + File.separator;
    }
    return CommonParameter.getInstance().getOutputDirectory();
  }

  public static Options getOptionsByDbName(String dbName) {
    if (hasProperty(dbName)) {
      return getProperty(dbName).getDbOptions();
    }
    return createDefaultDbOptions();
  }
}
