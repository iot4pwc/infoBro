package com.iot4pwc.verticles;
import java.io.UnsupportedEncodingException;
// There's a ton of unused imports here. Let's clean that up. Will create a ticket for this.
import java.net.URLDecoder;
import java.util.List;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.iot4pwc.components.helpers.DBHelper;
import com.iot4pwc.components.tables.RoomFileShare;
import com.iot4pwc.components.tables.RoomOccupancy;
import com.iot4pwc.components.tables.UserDetail;
import com.iot4pwc.constants.ConstLib;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class BackendServer extends AbstractVerticle {

  //TODO: Logging does not work yet.
  Logger logger;
  
  @Override
  public void start() {
    Router router = Router.router(vertx);

  //TODO: Logging does not work yet.
    Logger logger = LogManager.getLogger(BackendServer.class);

    logger.info("Initializing RESTful service running on port 8080");
    
    router.route().handler(BodyHandler.create());
    router.get("/mapUUIDs").handler(this::mapUUIDs);
    router.post("/:meetingRoomID/checkin").handler(this::checkin);
    router.delete("/:meetingRoomID/checkout").handler(this::checkout);
    router.get("/:meetingRoomID/getParticipants").handler(this::getParticipants);
    router.get("/:meetingRoomID/getRoomInformation").handler(this::getRoomInformation);
    router.get("/:meetingRoomID/getFiles").handler(this::getFiles);
    router.post("/:meetingRoomID/postFile").handler(this::postFile);
    router.delete("/:meetingRoomID/deleteFile").handler(this::deleteFile);

    vertx.createHttpServer().requestHandler(router::accept).listen(8080, "127.0.0.1");
    
    logger.info("RESTful service running on port 8080");
  }

  private void mapUUIDs(RoutingContext routingContext) {
    logger.info("GET " + routingContext.request().uri());
    String allUUIDs = routingContext.request().getParam("uuid");

    // Prepare the IN clause
    allUUIDs = allUUIDs.replaceAll(",", "','");

    // Get the JSON object from the DB
    List<JsonObject> result = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .select("SELECT uuid_room.uuid, uuid_room.room_id, room_info.room_name "
            + "FROM uuid_room "
            + "JOIN room_info "
            + "ON room_info.room_id = uuid_room.room_id "
            + "WHERE uuid_room.uuid IN ('" + allUUIDs + "');");

    JsonArray arr = new JsonArray(result);
    JsonObject obj = new JsonObject().put("result", arr);
    routingContext.response()
    .putHeader("content-type", "application/json; charset=utf-8")
    .setStatusCode(200)
    .end(obj.encodePrettily());
  }

  private void checkin(RoutingContext routingContext) { 
    logger.info("POST " + routingContext.request().uri());
    JsonObject body = routingContext.getBodyAsJson();
    logger.info(routingContext.getBodyAsString());

    if(body.isEmpty() 
        || !body.containsKey("hostFlag") 
        || !body.containsKey("email") 
        || !body.containsKey("firstName")
        || !body.containsKey("lastName")
        || !body.containsKey("dateOfBirth") 
        || !body.containsKey("resumeLink")
        || !body.containsKey("profilePicture")
        || !body.containsKey("position") 
        || !body.containsKey("company")) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String meetingRoom = routingContext.request().getParam("meetingRoomID");
      boolean hostFlag = body.getBoolean("hostFlag");
      String email = body.getString("email");
      String firstName = body.getString("firstName");
      String lastName = body.getString("lastName");
      String dateOfBirth = body.getString("dateOfBirth");
      String resumeLink = body.getString("resumeLink");
      String profilePicture = body.getString("profilePicture");
      String position = body.getString("position");
      String company = body.getString("company");

      logger.info("Current host token: " + getHostToken(meetingRoom));

      String hostToken;

      // host_token will be only set for the host.
      if (hostFlag == true) {
        // If there is no host currently, assign a new token.
        if (getHostToken(meetingRoom) == null) {
          hostToken = generateHostToken();

          // Else, ignore request for a new host token if from a new email.
        } else {
          // But not if it is from the same email.
          if (email.equals(getHostEmail(meetingRoom))) {
            logger.info("Saved host token: " + getHostToken(meetingRoom));
            hostToken = getHostToken(meetingRoom);
          } else {
            hostToken = "";
          }
        } 

        // Non-hosts do not get tokens.
      } else {
        logger.info("Saved host token: " + getHostToken(meetingRoom));
        hostToken = "";
      }

      // Assemble the recordObject to be inserted into the meeting_room_occupancy table.
      JsonObject recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("room_id", new Integer(meetingRoom));
      recordObject.put("host_token", hostToken);

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).replace(recordObject, RoomOccupancy.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "email");
      recordObject.put("info_value", email);
      recordObject.put("info_type", "text");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).replace(recordObject, UserDetail.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "firstName");
      recordObject.put("info_value", firstName);
      recordObject.put("info_type", "text");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).replace(recordObject, UserDetail.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "lastName");
      recordObject.put("info_value", lastName);
      recordObject.put("info_type", "text");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).replace(recordObject, UserDetail.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "dateOfBirth");
      recordObject.put("info_value", dateOfBirth);
      recordObject.put("info_type", "text");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).insert(recordObject, UserDetail.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "resumeLink");
      recordObject.put("info_value", resumeLink);
      recordObject.put("info_type", "url");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).replace(recordObject, UserDetail.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "profilePicture");
      recordObject.put("info_value", profilePicture);
      recordObject.put("info_type", "image");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).insert(recordObject, UserDetail.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "position");
      recordObject.put("info_value", position);
      recordObject.put("info_type", "text");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).replace(recordObject, UserDetail.getInstance());

      // Assemble the recordObject to be inserted into the user table.
      recordObject = new JsonObject();
      recordObject.put("user_email", email);
      recordObject.put("info_key", "company");
      recordObject.put("info_value", company);
      recordObject.put("info_type", "text");

      // Do the insertion
      DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).insert(recordObject, UserDetail.getInstance());

      // Respond with correct HTTP code.
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(200)
      .end(); 
    }
  }

  private void checkout(RoutingContext routingContext){
    logger.info("DELETE " + routingContext.request().uri());
    JsonObject body = routingContext.getBodyAsJson();
    logger.info(routingContext.getBodyAsString());

    if(body.isEmpty() 
        || !body.containsKey("email")) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String meetingRoom = routingContext.request().getParam("meetingRoomID");
      String email = body.getString("email");
      String token = body.getString("token"); 

      // We need to do three things here:
      String actualHostToken = getHostToken(meetingRoom);      

      logger.info("Actual host token is " + actualHostToken);
      logger.info("Similarity check: " + isFancyIdentical(actualHostToken, actualHostToken, token, 5));

      int numberOfCharactersIdentical = 5;

      // Checkout of host will delete all files in meeting room and check everybody else out.
      if (isFancyIdentical(actualHostToken, actualHostToken, token, numberOfCharactersIdentical)) {
        // doSendEmail(ListOfRecords, email); --> NOT IN MVP.

        // Delete the files of the particular meeting room
        // DELETE FROM meeting_room_files
        // WHERE meeting_room = meetingRoom;
        DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .delete("DELETE FROM room_fileshare "
            + "WHERE room_id = '" + meetingRoom + "';"); 

        // (2) Check everybody out.
        // Changes will cascade to User Detail.
        DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .delete("DELETE FROM room_occupancy "
            + "WHERE room_id = '" + meetingRoom +"';");

        // Checkout of someone pretending to be host but with wrong token will trigger 400. 
      } else if (!isFancyIdentical(actualHostToken, actualHostToken, token, numberOfCharactersIdentical) 
          && getHostEmail(meetingRoom).equals(email)){
        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(400)
        .end(); 
        return;

        // Checkout of non-host will only delete that person's information.
      } else {
        DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .delete("DELETE FROM room_occupancy "
            + "WHERE user_email = '" + email + "';"); 
      }

      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(200)
      .end(); 
    } 
  }

  private void getParticipants(RoutingContext routingContext){
    String meetingRoom = routingContext.request().getParam("meetingRoomID");

    try {
      meetingRoom = URLDecoder.decode(meetingRoom, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    // Get the JSON object from the DB
    List<JsonObject> result = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .select("SELECT user_detail.user_email, user_detail.info_key, user_detail.info_value "
            + "FROM room_occupancy JOIN user_detail "
            + "ON room_occupancy.user_email = user_detail.user_email "
            + "WHERE room_occupancy.room_id = '" + meetingRoom + "';");


    // Assemble the users object.
    JsonObject allParticipants = new JsonObject();

    for (JsonObject aResult : result) {
      JsonObject oneParticipant;
      String userName = aResult.getString("user_email");
      if (!allParticipants.containsKey(userName)) {
        oneParticipant = new JsonObject();
      } else {
        oneParticipant = allParticipants.getJsonObject(userName);
      }
      oneParticipant.put(aResult.getString("info_key"), aResult.getString("info_value"));
      allParticipants.put(userName, oneParticipant);
    }

    routingContext.response()
    .putHeader("content-type", "application/json; charset=utf-8")
    .setStatusCode(200)
    .end(allParticipants.encodePrettily()); 
  }

  private void getRoomInformation (RoutingContext routingContext) { 
    String meetingRoom = routingContext.request().getParam("meetingRoomID");

    try {
      meetingRoom = URLDecoder.decode(meetingRoom, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    // (1) Get meeting room information from the DB
    // SELECT asset_name, value, type 
    // FROM meeting_room_info
    // WHERE meeting_room_name = meetingRoom

    // Get the JSON object from the DB
    List<JsonObject> result = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .select("SELECT info_key, info_value, info_type "
            + "FROM room_details "
            + "WHERE room_id = '" + meetingRoom + "';");

    // Assemble the files object.
    JsonObject allFiles = new JsonObject();

    logger.info(result);

    for (JsonObject aResult : result) {
      JsonObject oneFile;
      String fileName = aResult.getString("info_key");
      if (!allFiles.containsKey(fileName)) {
        oneFile = new JsonObject();
      } else {
        oneFile = allFiles.getJsonObject(fileName);
      }
      oneFile.put(aResult.getString("info_type"), aResult.getString("info_value"));
      allFiles.put(fileName, oneFile);
    }

    // Respond with correct HTTP code. 
    routingContext.response()
    .putHeader("content-type", "application/json; charset=utf-8")
    .setStatusCode(200)
    .end(allFiles.encodePrettily()); 
  }

  private void getFiles(RoutingContext routingContext) {
    String meetingRoom = routingContext.request().getParam("meetingRoomID");
    String accessCode = routingContext.request().getParam("accessCode");

    logger.info(meetingRoom + " " + accessCode);

    String actualHostToken = getHostToken(meetingRoom);
    int numberOfCharactersIdentical = 5; 
    
    if(accessCode == null) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else if (isFancyIdentical(actualHostToken, getMD5(actualHostToken), accessCode, numberOfCharactersIdentical)) {
   // Get the JSON object from the DB
      List<JsonObject> result = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
          .select("SELECT file_header, file_link, file_type "
              + "FROM room_fileshare "
              + "WHERE room_id = '" + meetingRoom + "';");

      // Assemble the files object.
      JsonObject allFiles = new JsonObject();

      System.out.println(result);

      for (JsonObject aResult : result) {
        JsonObject oneFile;
        String fileName = aResult.getString("file_header");
        if (!allFiles.containsKey(fileName)) {
          oneFile = new JsonObject();
        } else {
          oneFile = allFiles.getJsonObject(fileName);
        }
        oneFile.put(aResult.getString("file_type"), aResult.getString("file_link"));
        allFiles.put(fileName, oneFile);
      }

      // Respond with correct HTTP code. 
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(200)
      .end(allFiles.encodePrettily()); 
    } else {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    }
  }

  private void postFile(RoutingContext routingContext) {
    String meetingRoom = routingContext.request().getParam("meetingRoomID");

    System.out.println(BackendServer.class.getName()+" : POST FILE " + routingContext.request().uri());
    JsonObject body = routingContext.getBodyAsJson();
    System.out.println(routingContext.getBodyAsString());

    if (body.isEmpty() 
        || !body.containsKey("key") 
        || !body.containsKey("value")
        || !body.containsKey("accessCode")) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String accessCode = body.getString("accessCode");
      String key = body.getString("key");
      String value = body.getString("value");

      String actualHostToken = getHostToken(meetingRoom);      
      String hashedHostToken = getMD5(actualHostToken);

      System.out.println("Actual host token is " + actualHostToken);
      System.out.println("Hashed host token is " + hashedHostToken);  
      System.out.println("Similarity check: " + isFancyIdentical(actualHostToken, hashedHostToken, accessCode, 2));

      int numberOfCharactersIdentical = 5;
      if (isFancyIdentical(actualHostToken, getMD5(actualHostToken), accessCode, numberOfCharactersIdentical)) {

        JsonObject recordObject = new JsonObject();
        recordObject.put("room_id", meetingRoom);
        recordObject.put("file_header", key);
        recordObject.put("file_link", value);
        recordObject.put("file_type", "url");

        boolean insertionSuccess = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
            .replace(recordObject, RoomFileShare.getInstance());

        System.out.println("Insertion success: " + insertionSuccess);

        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(200)
        .end();
      } else {
        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(400)
        .end();
      }
    }
  }

  private void deleteFile(RoutingContext routingContext) {
    System.out.println(BackendServer.class.getName()+" : DELETE FILE" + routingContext.request().uri());
    JsonObject body = routingContext.getBodyAsJson();
    System.out.println(routingContext.getBodyAsString());

    if (body.isEmpty() 
        || !body.containsKey("fileKey")
        || !body.containsKey("accessCode")) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String meetingRoom = routingContext.request().getParam("meetingRoomID");
      String fileKey = body.getString("fileKey");
      String accessCode = body.getString("accessCode");

      String actualHostToken = getHostToken(meetingRoom);      
      String hashedHostToken = getMD5(actualHostToken);

      System.out.println("Actual host token is " + actualHostToken);
      System.out.println("Hashed host token is " + hashedHostToken);  
      System.out.println("Similarity check: " + isFancyIdentical(actualHostToken, actualHostToken, accessCode, 2));

      int numberOfCharactersIdentical = 5;

      if (isFancyIdentical(actualHostToken, actualHostToken, accessCode, numberOfCharactersIdentical)) {
        DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER).delete("DELETE FROM room_fileshare "
            + "WHERE file_header = '" + fileKey + "' "
            + "AND room_id = '" + meetingRoom + "';");

        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(200)
        .end();

      } else {
        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(400)
        .end();
      } 
    }
  }

  private String getHostToken(String meetingRoomID) {
    List<JsonObject> result = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .select("SELECT host_token "
            + "FROM room_occupancy "
            + "WHERE room_id = '" + meetingRoomID + "'"
            + "AND host_token IS NOT NULL "
            + "AND host_token != '';");
    if (result.size() > 0) {
      System.out.println(result);
      return result.get(0).getString("host_token");
    }
    else {
      System.out.println(result);
      return null;
    }
  }

  private String getHostEmail(String meetingRoomID) {
    List<JsonObject> result = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER)
        .select("SELECT user_email "
            + "FROM room_occupancy "
            + "WHERE room_id = '" + meetingRoomID + "'"
            + "AND host_token IS NOT NULL "
            + "AND host_token != '';");

    return result.get(0).getString("user_email");
  }

  private String getMD5(String unhashed) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
      byte[] array = md.digest(unhashed.getBytes());
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < array.length; ++i) {
        sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
      }
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
    }
    return null;
  }

  private boolean isFancyIdentical(String unhashed, String hashed, String provided, int numberOfCharacters) {
    if (provided.substring(0, Math.min(provided.length(), numberOfCharacters))
        .equals(unhashed.substring(0, Math.min(unhashed.length(), numberOfCharacters))) 
        || provided.substring(0, Math.min(provided.length(), numberOfCharacters))
        .equals(hashed.substring(0, Math.min(hashed.length(), numberOfCharacters)))) {
      return true;
    }
    return false;
  }

  private String generateHostToken() {
    StringBuilder sb = new StringBuilder();
    String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    int N = alphabet.length();
    Random r = new Random();

    for (int i = 0; i < 6; i++) {
      sb.append(alphabet.charAt(r.nextInt(N)));
    }

    return sb.toString();
  }
}

