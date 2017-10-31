package com.iot4pwc.constants;

import org.apache.logging.log4j.Level;

public class ConstLib {
  public static final String MYSQL_CONNECTION_STRING = "jdbc:mysql://%s/%s?autoReconnect=true&useSSL=false";
  public static final String INFORMATION_BROADCASTER = "information_broadcaster";
  public static final String HIKARI_POOL_NAME = "DBHelper connection pool";
  public static final int HIKARI_MAX_POOL_SIZE = 4;
  public static final boolean HIKARI_CACHE_PSTMT = true;
  public static final int HIKARI_PSTMT_CACHE_SIZE = 256;
  public static final boolean HIKARI_USE_SERVER_PSTMT = true;
  
  //TODO: Logging not working for now.
  public static final Level LOGGING_LEVEL = Level.INFO;
  public static final String LOGGING_CONFIG = "src/main/resources/log4j2.xml";
}