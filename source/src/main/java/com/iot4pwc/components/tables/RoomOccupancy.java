package com.iot4pwc.components.tables;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import io.vertx.core.json.JsonObject;

public class RoomOccupancy extends Queriable {

  public static final String tableName = "room_occupancy";
  public static final String user = "user_email";
  public static final String meetingRoom = "room_id";
  public static final String hostToken = "host_token";
  private static RoomOccupancy tableInstance;
  
  // Added this constant here because it logically belongs to RoomOccupancy, though it is not used.
  public static final String hostFlag = "host_flag";
  public static final String token = "token";

  public static RoomOccupancy getInstance() {
    if (tableInstance == null) {
      return new RoomOccupancy();
    } else {
      return tableInstance;
    }
  }
  
  @Override
  public void configureInsertPstmt(PreparedStatement pstmt, JsonObject recordObject,
      List<String> attributeNames) {
    int counter = 1;
    try {
      for (String attributeName : attributeNames) {
        switch (attributeName) {
          case user: {
            pstmt.setString(counter++, recordObject.getString(attributeName));
            break;
          }
          case meetingRoom: {
            pstmt.setInt(counter++, recordObject.getInteger(attributeName));
            break;
          }
          case hostToken: {
            pstmt.setString(counter++, recordObject.getString(attributeName));
            break;          
          }
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
  
  @Override
  public String getTableName() {
    return tableName;
  }

}
