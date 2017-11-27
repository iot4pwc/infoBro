# Information Broadcaster
Information Broadcaster is an application that is built for information exchange in meetings. Its frontend is a React-native based android application, which can be found in the RNInformationBroadcaster repository. Its backend is located in this very repository.

To run this application, first deploy the backend RESTful service according to the instructions below:

# Scripts
Before deploying the Information Broadcaster backend service, make sure scripts/setup.sh is properly executed:
```
source setup.sh
```

# Executables
After running the scripts, compile a executable fat jar with Maven
```
mvn clean package
```

Then copy the fat jar to the instance the RESTful service will be run, then:
```
java -jar yourFatJarName-1.0.jar
```

# APK
Once the backend service is deployed, compile an APK from the frontend repository mentioend in the previous section. Make sure you change the host address in the constants/common.js before you compile.

# Register Beacon
Before running the application, make sure you register the beacon to the beacon repository. Use Beacon Finder or other similar applications to get the UUID of the UDOO-neo beacon(currently this app will only work with UDOO-neo beacon), then map it to a room in the MySQL database. Use MySQL CLI to register as currently there is no UI for registration.

# Finally
Use the application near a beacon.
