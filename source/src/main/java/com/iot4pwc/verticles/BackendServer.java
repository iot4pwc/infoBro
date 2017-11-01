package com.iot4pwc.verticles;
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
//import io.vertx.core.WorkerExecutor;  // --> This import is problematic.
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class BackendServer extends AbstractVerticle {

  //TODO: Logging does not work yet.
  Logger logger;
  DBHelper dbHelper;

  @Override
  public void start() {
    Router router = Router.router(vertx);

    //TODO: Logging does not work yet.
    logger = LogManager.getLogger(BackendServer.class);

    //    WorkerExecutor executor = vertx.createSharedWorkerExecutor(
    //        ConstLib.BACKEND_WORKER_EXECUTOR_POOL,
    //        ConstLib.BACKEND_WORKER_POOL_SIZE
    //        );
    //    executor.executeBlocking (future -> {
    dbHelper = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER);
    //    });

    logger.info("Initializing RESTful service running on port " + ConstLib.HTTP_SERVER_PORT);

    router.route().handler(BodyHandler.create());
    router.get("/mapUUIDs").handler(this::mapUUIDs);
    router.post("/:meetingRoomID/checkin").handler(this::checkin);
    router.delete("/:meetingRoomID/checkout").handler(this::checkout);
    router.get("/:meetingRoomID/getParticipants").handler(this::getParticipants);
    router.get("/:meetingRoomID/getRoomInformation").handler(this::getRoomInformation);
    router.get("/:meetingRoomID/getFiles").handler(this::getFiles);
    router.post("/:meetingRoomID/postFile").handler(this::postFile);
    router.delete("/:meetingRoomID/deleteFile").handler(this::deleteFile);

    vertx.createHttpServer(
        new HttpServerOptions()
//        .setSsl(true)
//        .setPemKeyCertOptions(
//            new PemKeyCertOptions()
//            .setKeyPath(ConstLib.PRIVATE_KEY_PATH)
//            .setCertPath(ConstLib.CERTIFICATE_PATH))
        ).requestHandler(router::accept).listen(ConstLib.HTTP_SERVER_PORT, ConstLib.HTTP_SERVER_IP);

    logger.info("RESTful service running on port " + ConstLib.HTTP_SERVER_PORT);
  }

  private void mapUUIDs(RoutingContext routingContext) {
    logger.info("GET " + routingContext.request().uri());
    String allUUIDs = routingContext.request().getParam(ConstLib.UUID_PARAMETER);

    // Prepare the IN clause
    allUUIDs = allUUIDs.replaceAll(",", "','");

    // Get the JSON object from the DB
    List<JsonObject> result = dbHelper
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
        || !body.containsKey(RoomOccupancy.hostFlag) 
        || !body.containsKey(RoomOccupancy.user) 
        || !body.containsKey(UserDetail.firstName)
        || !body.containsKey(UserDetail.lastName)
        || !body.containsKey(UserDetail.dateOfBirth) 
        || !body.containsKey(UserDetail.resumeLink)
        || !body.containsKey(UserDetail.profilePicture)
        || !body.containsKey(UserDetail.position) 
        || !body.containsKey(UserDetail.company)) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);
      boolean hostFlag = body.getBoolean(RoomOccupancy.hostFlag);
      String email = body.getString(RoomOccupancy.user);

      logger.info("Current host token: " + getHostToken(meetingRoom));

      String hostToken;
      JsonObject response = new JsonObject();


      // host_token will be only set for the host.
      if (hostFlag) {
        // If there is no host currently, assign a new token.
        if (getHostToken(meetingRoom) == null) {
          hostToken = generateHostToken();

          response.put("host_token", hostToken);
          response.put("hashed_host_token", getMD5(hostToken));

          // Else, ignore request for a new host token if from a new email.
        } else {
          // But not if it is from the same email.
          if (email.equals(getHostEmail(meetingRoom))) {
            logger.info("Saved host token: " + getHostToken(meetingRoom));
            hostToken = getHostToken(meetingRoom);
          } else {
            hostToken = ConstLib.NORMAL_USER;
          }
        } 

        // Non-hosts do not get tokens.
      } else {
        logger.info("Saved host token: " + getHostToken(meetingRoom));
        hostToken = ConstLib.NORMAL_USER;
      }

      // Assemble the recordObject to be inserted into the meeting_room_occupancy table.
      JsonObject recordObject = new JsonObject();
      recordObject.put(RoomOccupancy.user, email);
      recordObject.put(RoomOccupancy.meetingRoom, new Integer(meetingRoom));
      recordObject.put(RoomOccupancy.hostToken, hostToken);

      // Do the insertion
      dbHelper.insert(recordObject, RoomOccupancy.getInstance(), true);

      // Insert all key-value pairs.
      insertAllUserInformation(body); 

      // Respond with correct HTTP code.
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(200)
      .end(response.encodePrettily()); 
    }
  }

  private void checkout(RoutingContext routingContext){
    logger.info("DELETE " + routingContext.request().uri());
    JsonObject body = routingContext.getBodyAsJson();
    logger.info(routingContext.getBodyAsString());

    if(body.isEmpty() 
        || !body.containsKey(RoomOccupancy.user)) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);
      String email = body.getString(RoomOccupancy.user);
      String token = body.getString(RoomOccupancy.token); 

      // We need to do three things here:
      String actualHostToken = getHostToken(meetingRoom);      

      //      logger.info("Actual host token is " + actualHostToken);
      logger.info("Similarity check: " + isFancyIdentical(actualHostToken, actualHostToken, token, 5));

      // Checkout of host will delete all files in meeting room and check everybody else out.
      if (isFancyIdentical(actualHostToken, actualHostToken, token, ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY)) {
        // doSendEmail(ListOfRecords, email); --> NOT IN MVP.

        // Delete the files of the particular meeting room
        // DELETE FROM meeting_room_files
        // WHERE meeting_room = meetingRoom;
        dbHelper
        .delete("DELETE FROM room_fileshare "
            + "WHERE room_id = '" + meetingRoom + "';"); 

        // (2) Check everybody out.
        // Changes will cascade to User Detail.
        dbHelper
        .delete("DELETE FROM room_occupancy "
            + "WHERE room_id = '" + meetingRoom +"';");

        // Checkout of someone pretending to be host but with wrong token will trigger 400. 
      } else if (!isFancyIdentical(actualHostToken, actualHostToken, token, ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY) 
          && getHostEmail(meetingRoom).equals(email)){
        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(400)
        .end(); 
        return;

        // Checkout of non-host will only delete that person's information.
      } else {
        dbHelper
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
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);

    // Get the JSON object from the DB
    List<JsonObject> result = dbHelper
        .select("SELECT user_detail.user_email, user_detail.info_key, user_detail.info_value, room_occupancy.host_token "
            + "FROM room_occupancy JOIN user_detail "
            + "ON room_occupancy.user_email = user_detail.user_email "
            + "WHERE room_id = '" + meetingRoom + "';");


    // Assemble the users object.
    JsonObject allParticipants = new JsonObject();

    for (JsonObject aResult : result) {
      JsonObject oneParticipant;
      String userName = aResult.getString(UserDetail.user);
      if (!allParticipants.containsKey(userName)) {
        oneParticipant = new JsonObject();
        boolean is_host = aResult.getString("host_token").equals("") ? false : true;
        oneParticipant.put("is_host", is_host);
      } else {
        oneParticipant = allParticipants.getJsonObject(userName);
      }
      oneParticipant.put(aResult.getString(UserDetail.asset), aResult.getString(UserDetail.value));
      allParticipants.put(userName, oneParticipant);
    }

    routingContext.response()
    .putHeader("content-type", "application/json; charset=utf-8")
    .setStatusCode(200)
    .end(allParticipants.encodePrettily()); 
  }

  private void getRoomInformation (RoutingContext routingContext) { 
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);

    // (1) Get meeting room information from the DB
    // SELECT asset_name, value, type 
    // FROM meeting_room_info
    // WHERE meeting_room_name = meetingRoom

    // Get the JSON object from the DB
    List<JsonObject> result = dbHelper
        .select("SELECT info_key, info_value, info_type "
            + "FROM room_details "
            + "WHERE room_id = '" + meetingRoom + "';");

    // Assemble the files object.
    JsonObject allFiles = new JsonObject();

    logger.info(result);

    for (JsonObject aResult : result) {
      JsonObject oneFile;
      String fileName = aResult.getString(ConstLib.ROOM_INFO_KEY);
      if (!allFiles.containsKey(fileName)) {
        oneFile = new JsonObject();
      } else {
        oneFile = allFiles.getJsonObject(fileName);
      }
      oneFile.put(aResult.getString(ConstLib.ROOM_INFO_TYPE), aResult.getString(ConstLib.ROOM_INFO_VALUE));
      allFiles.put(fileName, oneFile);
    }

    // Respond with correct HTTP code. 
    routingContext.response()
    .putHeader("content-type", "application/json; charset=utf-8")
    .setStatusCode(200)
    .end(allFiles.encodePrettily()); 
  }

  private void getFiles(RoutingContext routingContext) {
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);
    String accessCode = routingContext.request().getParam(ConstLib.ACCESS_CODE_PARAMETER);

    System.out.println(meetingRoom + " " + accessCode);

    String actualHostToken = getHostToken(meetingRoom);
    int numberOfCharactersIdentical = 5; 

    System.out.println(actualHostToken + " " + getMD5(actualHostToken));

    if(accessCode == null) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else if (isFancyIdentical(actualHostToken, getMD5(actualHostToken), accessCode, numberOfCharactersIdentical)) {
      // Get the JSON object from the DB
      List<JsonObject> result = dbHelper
          .select("SELECT file_header, file_link, file_type "
              + "FROM room_fileshare "
              + "WHERE room_id = '" + meetingRoom + "';");

      // Assemble the files object.
      JsonObject allFiles = new JsonObject();

      System.out.println(result);

      for (JsonObject aResult : result) {
        JsonObject oneFile;
        String fileName = aResult.getString(RoomFileShare.assetName);
        if (!allFiles.containsKey(fileName)) {
          oneFile = new JsonObject();
        } else {
          oneFile = allFiles.getJsonObject(fileName);
        }
        oneFile.put(aResult.getString(RoomFileShare.type), aResult.getString(RoomFileShare.value));
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
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);

    System.out.println(BackendServer.class.getName()+" : POST FILE " + routingContext.request().uri());
    JsonObject body = routingContext.getBodyAsJson();
    System.out.println(routingContext.getBodyAsString());

    if (body.isEmpty() 
        || !body.containsKey(RoomFileShare.assetName) 
        || !body.containsKey(RoomFileShare.value)
        || !body.containsKey(ConstLib.ACCESS_CODE_PARAMETER)) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String accessCode = body.getString(ConstLib.ACCESS_CODE_PARAMETER);
      String key = body.getString(RoomFileShare.assetName);
      String value = body.getString(RoomFileShare.value);

      String actualHostToken = getHostToken(meetingRoom);      
      String hashedHostToken = getMD5(actualHostToken);

      System.out.println("Actual host token is " + actualHostToken);
      System.out.println("Hashed host token is " + hashedHostToken);  
      System.out.println("Similarity check: " + isFancyIdentical(actualHostToken, hashedHostToken, accessCode, 2));

      int numberOfCharactersIdentical = 5;
      if (isFancyIdentical(actualHostToken, getMD5(actualHostToken), accessCode, numberOfCharactersIdentical)) {

        JsonObject recordObject = new JsonObject();
        recordObject.put(RoomFileShare.meetingRoom, meetingRoom);
        recordObject.put(RoomFileShare.assetName, key);
        recordObject.put(RoomFileShare.value, value);
        recordObject.put(RoomFileShare.type, "url");

        boolean insertionSuccess = dbHelper
            .insert(recordObject, RoomFileShare.getInstance(), true);

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
      String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);
      String fileKey = body.getString("fileKey");
      String accessCode = body.getString("accessCode");

      String actualHostToken = getHostToken(meetingRoom);      
      String hashedHostToken = getMD5(actualHostToken);

      System.out.println("Actual host token is " + actualHostToken);
      System.out.println("Hashed host token is " + hashedHostToken);  
      System.out.println("Similarity check: " + isFancyIdentical(actualHostToken, actualHostToken, accessCode, 2));

      int numberOfCharactersIdentical = 5;

      if (isFancyIdentical(actualHostToken, actualHostToken, accessCode, numberOfCharactersIdentical)) {
        dbHelper.delete("DELETE FROM room_fileshare "
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

  private void insertAllUserInformation(JsonObject body) {
    String email = body.getString(RoomOccupancy.user);
    String firstName = body.getString(UserDetail.firstName);
    String lastName = body.getString(UserDetail.lastName);
    String dateOfBirth = body.getString(UserDetail.dateOfBirth);
    String resumeLink = body.getString(UserDetail.resumeLink);
    String profilePicture = body.getString(UserDetail.profilePicture);
    String position = body.getString(UserDetail.position);
    String company = body.getString(UserDetail.company); 

    // Assemble the recordObject to be inserted into the user table.
    JsonObject recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.user);
    recordObject.put(UserDetail.value, email);
    recordObject.put(UserDetail.type, "text");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);

    // Assemble the recordObject to be inserted into the user table.
    recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.firstName);
    recordObject.put(UserDetail.value, firstName);
    recordObject.put(UserDetail.type, "text");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);

    // Assemble the recordObject to be inserted into the user table.
    recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.lastName);
    recordObject.put(UserDetail.value, lastName);
    recordObject.put(UserDetail.type, "text");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);

    // Assemble the recordObject to be inserted into the user table.
    recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.dateOfBirth);
    recordObject.put(UserDetail.value, dateOfBirth);
    recordObject.put(UserDetail.type, "text");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);

    // Assemble the recordObject to be inserted into the user table.
    recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.resumeLink);
    recordObject.put(UserDetail.value, resumeLink);
    recordObject.put(UserDetail.type, "url");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);

    // Assemble the recordObject to be inserted into the user table.
    recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.profilePicture);
    recordObject.put(UserDetail.value, profilePicture);
    recordObject.put(UserDetail.type, "image");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);

    // Assemble the recordObject to be inserted into the user table.
    recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.position);
    recordObject.put(UserDetail.value, position);
    recordObject.put(UserDetail.type, "text");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);

    // Assemble the recordObject to be inserted into the user table.
    recordObject = new JsonObject();
    recordObject.put(UserDetail.user, email);
    recordObject.put(UserDetail.asset, UserDetail.company);
    recordObject.put(UserDetail.value, company);
    recordObject.put(UserDetail.type, "text");

    // Do the insertion
    dbHelper.insert(recordObject, UserDetail.getInstance(), true);
  }

  private String getHostToken(String meetingRoomID) {
    List<JsonObject> result = dbHelper
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
    List<JsonObject> result = dbHelper
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

