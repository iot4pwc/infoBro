#!/bin/bash

sudo apt-get update
sudo apt-get install -y default-jre default-jdk
sudo chmod 777 /etc/environment
sudo echo "JAVA_HOME=\"/usr/lib/jvm/java-8-openjdk-amd64/jre\"" >> /etc/environment
source /etc/environment
# export mysql user name and password
DB_USER_NAME='iot4pwc'
export DB_USER_NAME
DB_USER_PW='Heinz123!'
export DB_USER_PW

sudo ufw allow 37288
echo "#################################################################################"
echo "Please input the public ip and port of the MySQL instance: E.g 18.221.182.91:3306"
read MYSQL_URL
export MYSQL_URL
