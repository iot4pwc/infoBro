package com.iot4pwc.verticles;

/**
 * RESTful backend for Information Broadcaster.
 * Author: Stefan Hermanek (shermane, hermanek.stefan@gmail.com)
 * Last-modified: 22 November 2017
 */

/** Required imports **/

// Java core.
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Logging
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Business-logic related imports
import com.iot4pwc.components.helpers.DBHelper;
import com.iot4pwc.components.tables.RoomFileShare;
import com.iot4pwc.components.tables.RoomOccupancy;
import com.iot4pwc.components.tables.UserDetail;
import com.iot4pwc.constants.ConstLib;

// vert.x
import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class BackendServer extends AbstractVerticle {
  Logger logger;
  DBHelper dbHelper;

  @Override
  public void start() {
    
    // Init logging
    logger = LogManager.getLogger(BackendServer.class);

    // Added filter.
    Router filter = Router.router(vertx);
    Router router = Router.router(vertx);

    // Obtain a DB connection in blocking way.
    vertx.executeBlocking(future -> {
      dbHelper = DBHelper.getInstance(ConstLib.INFORMATION_BROADCASTER);
      future.complete();
    }, res -> {

      // All request first have to pass the filter
      router.route("/*").handler(this::filter); 

      // Thereafter, "business-logic" routing kicks in 
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
          // If running HTTPS
          //          .setSsl(true)
          //          .setPemKeyCertOptions(
          //              new PemKeyCertOptions()
          //              .setKeyPath(ConstLib.PRIVATE_KEY_PATH)
          //              .setCertPath(ConstLib.CERTIFICATE_PATH))
          //          ).requestHandler(router::accept).listen(ConstLib.HTTPS_SERVER_PORT);
          // If running HTTP
          ).requestHandler(router::accept).listen(ConstLib.HTTP_SERVER_PORT);


      logger.info("RESTful service running on port " + ConstLib.HTTP_SERVER_PORT);
    });
  }

  /**
   * A filter that rejects all requests that do not contain meet requirements.
   * Specifically, the following requirement must be met:
   * ConstLib.REQUIRED_HEADER_KEY with ConstLib.REQUIRED_HEADER_VALUE in HTTP header
   * @param routingContext
   */
  private void filter(RoutingContext routingContext) {
    MultiMap headers = routingContext.request().headers();
    if (headers.contains(ConstLib.REQUIRED_HEADER_KEY)) {
      if (headers.get(ConstLib.REQUIRED_HEADER_KEY).equals(ConstLib.REQUIRED_HEADER_VALUE)) {
        routingContext.next();
      } else {
        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(400)
        .end();
      }
    }
    else {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    }
  }

  /**
   * API endpoint to obtain mapping from a list of UUIDs to room_id and room_name.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void mapUUIDs(RoutingContext routingContext) {
    logger.info("GET " + routingContext.request().uri());

    String allUUIDs = routingContext.request().getParam(ConstLib.UUID_PARAMETER);
    allUUIDs = allUUIDs.replaceAll(",", "','");

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

  /**
   * API endpoint to check in a given user to a given room.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void checkin(RoutingContext routingContext) {
    JsonObject body = routingContext.getBodyAsJson();
    logger.info("POST " + routingContext.request().uri());
    logger.info(routingContext.getBodyAsString());

    if (body.isEmpty()
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

      if (hostFlag) {
        // If there is no host currently, assign a new token.
        if (getHostToken(meetingRoom) == null) {
          hostToken = generateHostToken();

          response.put("host_token", hostToken);
          response.put("hashed_host_token", getMD5(hostToken));

          // Else, ignore request for a new host token if from a new email.
        } else {
          if (email.equals(getHostEmail(meetingRoom))) {
            logger.info("Saved host token: " + getHostToken(meetingRoom));
            hostToken = getHostToken(meetingRoom);
          } else {
            hostToken = ConstLib.NORMAL_USER;
          }
        }
      } else {
        logger.info("Saved host token: " + getHostToken(meetingRoom));
        hostToken = ConstLib.NORMAL_USER;
      }

      JsonObject recordObject = new JsonObject();
      recordObject.put(RoomOccupancy.user, email);
      recordObject.put(RoomOccupancy.meetingRoom, new Integer(meetingRoom));
      recordObject.put(RoomOccupancy.hostToken, hostToken);
      dbHelper.insert(recordObject, RoomOccupancy.getInstance(), true);
      insertAllUserInformation(body);

      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(200)
      .end(response.encodePrettily());
    }
  }

  /**
   * API endpoint to check out a given user from a given room.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void checkout(RoutingContext routingContext) {
    JsonObject body = routingContext.getBodyAsJson();
    logger.info("DELETE " + routingContext.request().uri());

    if (body.isEmpty()
        || !body.containsKey(RoomOccupancy.user)) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else {
      String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);
      String email = body.getString(RoomOccupancy.user);
      String token = body.getString(RoomOccupancy.token);

      String actualHostToken = getHostToken(meetingRoom);

      // No host in the meeting.
      if (actualHostToken == null) {
        logger.info("Actual host token not defined / null.");
        dbHelper
        .delete("DELETE FROM room_occupancy "
            + "WHERE user_email = '" + email + "';");
        routingContext.response()
        .putHeader("content-type", "application/json; charset=utf-8")
        .setStatusCode(200)
        .end();
        return;
      }

      // Checkout of host will delete all files in meeting room and check everybody else out.
      if (token != null) {
        if (isFancyIdentical(actualHostToken, actualHostToken, token, ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY)) {
          dbHelper
          .delete("DELETE FROM room_fileshare "
              + "WHERE room_id = '" + meetingRoom + "';");

          // (2) Check host out.
          dbHelper
          .delete("DELETE FROM room_occupancy "
              + "WHERE user_email = '" + email + "';");

          System.out.println("Deleting all the files.");

          // Checkout of someone pretending to be host but with wrong token will trigger 400. 
        } else if (!isFancyIdentical(actualHostToken, actualHostToken, token, ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY)
            && getHostEmail(meetingRoom).equals(email)) {
          routingContext.response()
          .putHeader("content-type", "application/json; charset=utf-8")
          .setStatusCode(400)
          .end();
          return;

          // Checkout of non-host will only delete that person's information.
        }
      } else {
        dbHelper
        .delete("DELETE FROM room_occupancy "
            + "WHERE user_email = '" + email + "';");
        System.out.println("Deleting the attendee.");

      }

      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(200)
      .end();
    }
  }

  /**
   * API endpoint to get all participants in a given room.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void getParticipants(RoutingContext routingContext) {
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);

    List<JsonObject> result = dbHelper
        .select("SELECT user_detail.user_email, user_detail.info_key, user_detail.info_value, room_occupancy.host_token "
            + "FROM room_occupancy JOIN user_detail "
            + "ON room_occupancy.user_email = user_detail.user_email "
            + "WHERE room_id = '" + meetingRoom + "';");

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

  /**
   * API endpoint to get all room information (e.g. instructions) associated with a given room.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void getRoomInformation(RoutingContext routingContext) {
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);

    // (1) Get meeting room information from the DB
    List<JsonObject> result = dbHelper
        .select("SELECT info_key, info_value, info_type "
            + "FROM room_details "
            + "WHERE room_id = '" + meetingRoom + "';");

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

    routingContext.response()
    .putHeader("content-type", "application/json; charset=utf-8")
    .setStatusCode(200)
    .end(allFiles.encodePrettily());
  }

  /**
   * API endpoint to get all files in a given room.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void getFiles(RoutingContext routingContext) {
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);
    String accessCode = routingContext.request().getParam(ConstLib.ACCESS_CODE_PARAMETER);

    String actualHostToken = getHostToken(meetingRoom);

    if (accessCode == null) {
      routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .setStatusCode(400)
      .end();
    } else if (isFancyIdentical(actualHostToken, getMD5(actualHostToken), accessCode, ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY)) {
      // Get the JSON object from the DB
      List<JsonObject> result = dbHelper
          .select("SELECT file_header, file_link, file_type "
              + "FROM room_fileshare "
              + "WHERE room_id = '" + meetingRoom + "';");

      JsonObject allFiles = new JsonObject();

      logger.info(result);

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

  /**
   * API endpoint to post a file to the context of a given room.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void postFile(RoutingContext routingContext) {
    String meetingRoom = routingContext.request().getParam(ConstLib.MEETING_ROOM_ID_URL_PATTERN);
    JsonObject body = routingContext.getBodyAsJson();

    logger.info(BackendServer.class.getName() + " : POST FILE " + routingContext.request().uri());
    logger.info(routingContext.getBodyAsString());

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

      logger.info("Actual host token is " + actualHostToken);
      logger.info("Hashed host token is " + hashedHostToken);
      logger.info("Similarity check: " + isFancyIdentical(actualHostToken, hashedHostToken, accessCode, 2));

      if (isFancyIdentical(actualHostToken, getMD5(actualHostToken), accessCode, ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY)) {
        JsonObject recordObject = new JsonObject();
        recordObject.put(RoomFileShare.meetingRoom, meetingRoom);
        recordObject.put(RoomFileShare.assetName, key);
        recordObject.put(RoomFileShare.value, value);
        recordObject.put(RoomFileShare.type, "url");

        boolean insertionSuccess = dbHelper
            .insert(recordObject, RoomFileShare.getInstance(), true);

        logger.info("Insertion success: " + insertionSuccess);

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

  /**
   * API endpoint to delete one particular file from a given room.
   * For more details, see the API docs on Google Drive: https://docs.google.com/presentation/d/1wW-ab9f7PL5swt90ESjxOoFRI8NOfLTHJnmC2rPUezI/edit#slide=id.g2994ed1fb8_0_0
   * @param routingContext
   */
  private void deleteFile(RoutingContext routingContext) {
    logger.info(BackendServer.class.getName() + " : DELETE FILE" + routingContext.request().uri());
    JsonObject body = routingContext.getBodyAsJson();
    logger.info(routingContext.getBodyAsString());

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

      logger.info("Actual host token is " + actualHostToken);
      logger.info("Hashed host token is " + hashedHostToken);
      logger.info("Similarity check: " + isFancyIdentical(actualHostToken, actualHostToken, accessCode, 2));

      if (isFancyIdentical(actualHostToken, actualHostToken, accessCode, ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY)) {
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

  /**
   * Private helper function to insert complete user information into the DB.
   * @param body The JSON body from which to extract user information
   */
  private void insertAllUserInformation(JsonObject body) {
    JsonObject recordObject;
    String userEmail = body.getString(RoomOccupancy.user);  // used over and over again --> bring to method scope

    // Acquire the mapping of keys to type
    Map<String[], String> arrayOfKeysToTypeMapping = new HashMap<String[], String>();
    arrayOfKeysToTypeMapping.put(ConstLib.REQUIRED_TEXT_KEYS, "text");
    arrayOfKeysToTypeMapping.put(ConstLib.REQUIRED_URL_KEYS, "url");
    arrayOfKeysToTypeMapping.put(ConstLib.REQUIRED_IMAGE_KEYS, "image");

    // Fancy for loop for Xianru's enjoyment ;)
    for (String[] oneCategoryOfKeys: arrayOfKeysToTypeMapping.keySet()) {
      for (String oneTextKey: oneCategoryOfKeys) {
        recordObject = new JsonObject();
        recordObject.put(UserDetail.user, userEmail);
        recordObject.put(UserDetail.asset, oneTextKey);
        recordObject.put(UserDetail.value, body.getString(oneTextKey));
        recordObject.put(UserDetail.type, arrayOfKeysToTypeMapping.get(oneCategoryOfKeys));
        dbHelper.insert(recordObject, UserDetail.getInstance(), true);
      }
    }
  }

  /**
   * Helper function to obtain the hostToken for a given meeting room.
   * @param meetingRoomID Given meeting room
   * @return The host token as String.
   */
  private String getHostToken(String meetingRoomID) {
    List<JsonObject> result = dbHelper
        .select("SELECT host_token "
            + "FROM room_occupancy "
            + "WHERE room_id = '" + meetingRoomID + "'"
            + "AND host_token IS NOT NULL "
            + "AND host_token != '';");
    if (result.size() > 0) {
      logger.info(result);
      return result.get(0).getString("host_token");
    } else {
      logger.info(result);
      return null;
    }
  }

  /**
   * Helper function to obtain the email of the host of a given meeting room
   * @param meetingRoomID Given meeting room
   * @return The host email as String.
   */
  private String getHostEmail(String meetingRoomID) {
    List<JsonObject> result = dbHelper
        .select("SELECT user_email "
            + "FROM room_occupancy "
            + "WHERE room_id = '" + meetingRoomID + "'"
            + "AND host_token IS NOT NULL "
            + "AND host_token != '';");

    String returnValue;
    try {
      returnValue = result.get(0).getString("user_email");
    } catch (Exception e) {
      e.printStackTrace();
      returnValue = "NO_HOST_EMAIL_FOUND";
    }
    return returnValue;
  }

  /**
   * Helper function to obtain MD5 hash of a given String.
   * @param unHashed A regular String
   * @return The MD5 hash of said String.
   */
  private String getMD5(String unHashed) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
      byte[] array = md.digest(unHashed.getBytes());
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < array.length; ++i) {
        sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
      }
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
    }
    return null;
  }

  /**
   * A fancy method for checking String similarity between three things, up to the first n letters
   * 
   * If @provided matches either @hashed or @unhashed, method returns true.
   * Else, false.
   * 
   * @param unhashed String 1
   * @param hashed String 2
   * @param provided Questionable String
   * @param numberOfCharacters The number of letters to consider
   * @return True if provided matches with either hashed or unhashed, False otherwise. 
   */
  private boolean isFancyIdentical(String unhashed, String hashed, String provided, int numberOfCharacters) {
    if (provided != null) {
      if (provided.substring(0, Math.min(provided.length(), numberOfCharacters))
          .equals(unhashed.substring(0, Math.min(unhashed.length(), numberOfCharacters)))
          || provided.substring(0, Math.min(provided.length(), numberOfCharacters))
          .equals(hashed.substring(0, Math.min(hashed.length(), numberOfCharacters)))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Helper function to generate a host token.
   * @return A String of the host token.
   */
  private String generateHostToken() {
    StringBuilder sb = new StringBuilder();
    String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    int N = alphabet.length();
    Random r = new Random();

    // Changed to ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY
    for (int i = 0; i < ConstLib.CHARACTERS_REQUIRED_FOR_SIMILARITY; i++) {
      sb.append(alphabet.charAt(r.nextInt(N)));
    }

    return sb.toString();
  }
}

