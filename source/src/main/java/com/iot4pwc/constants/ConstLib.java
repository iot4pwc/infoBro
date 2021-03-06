package com.iot4pwc.constants;

import org.apache.logging.log4j.Level;
import com.iot4pwc.components.tables.RoomOccupancy;
import com.iot4pwc.components.tables.UserDetail;

public class ConstLib {
  public static final String BACKEND_WORKER_EXECUTOR_POOL = "";
  public static final int BACKEND_WORKER_POOL_SIZE = 2;
  
  public static final int CLUSTER_EVENT_BUS_PORT = 37288;
  
  public static final String MYSQL_CONNECTION_STRING = "jdbc:mysql://%s/%s?autoReconnect=true&useSSL=false";
  public static final String INFORMATION_BROADCASTER = "information_broadcaster";
  public static final String HIKARI_POOL_NAME = "DBHelper connection pool";
  public static final int HIKARI_MAX_POOL_SIZE = 4;
  public static final boolean HIKARI_CACHE_PSTMT = true;
  public static final int HIKARI_PSTMT_CACHE_SIZE = 256;
  public static final boolean HIKARI_USE_SERVER_PSTMT = true;
  
  // HTTP related constants.
  public static final String HTTP_SERVER_IP = "127.0.0.1";
  public static final int HTTP_SERVER_PORT = 8080;
  public static final int HTTPS_SERVER_PORT = 8443;
  
  // SSL related constants
  public static final String PRIVATE_KEY_PATH = "ca.key";
  public static final String CERTIFICATE_PATH = "ca.crt";
  
  // Business logic related constants
  public static final String NORMAL_USER = "";
  public static final String MEETING_ROOM_ID_URL_PATTERN = "meetingRoomID";
  public static final String UUID_PARAMETER = "uuid";
  public static final String ACCESS_CODE_PARAMETER = "accessCode";
  public static final int CHARACTERS_REQUIRED_FOR_SIMILARITY = 5;
  
  // As we are not inserting into Room_Information table, there is no table.
  // Alas, the values were stored here.
  public static final String ROOM_INFO_KEY = "info_key";
  public static final String ROOM_INFO_VALUE = "info_value";
  public static final String ROOM_INFO_TYPE = "info_type";
  
  // 
  public static final String[] REQUIRED_TEXT_KEYS = { RoomOccupancy.user,
      UserDetail.firstName,
      UserDetail.lastName,
      UserDetail.dateOfBirth,
      UserDetail.position,
      UserDetail.company
  };

  public static final String[] REQUIRED_URL_KEYS = { UserDetail.resumeLink };

  public static final String[] REQUIRED_IMAGE_KEYS = { UserDetail.profilePicture };
  
  public static final String REQUIRED_HEADER_KEY = "secretKey";
  public static final String REQUIRED_HEADER_VALUE = "secretValue";
  
  //TODO: Logging not working for now.
  public static final Level LOGGING_LEVEL = Level.INFO;
  public static final String LOGGING_CONFIG = "log4j2.xml";
}