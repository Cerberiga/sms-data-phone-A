CS 211: Spring 15 - Topic 14: DATA SERVICE THROUGH SHORT MESSAGE SERVICE IN 4G LTE
Dennis Chan
Garrett Johnston
Uen-Tao Wang

README
This file contains the instructions for installing our applications on two separate phones, running the applications, and verifying the expected behavior.

I. Hardware Requirements:
Phone A - Rooted Android phone, OS version 2.2 - 5.0, SMS plan
Phone B - Android phone, OS version 2.2-5.0, SMS plan, data connectivity (via WiFi or 3G/4G)

II. Software Requirements:
SMS Data A app
SMS Data B app
hw executable
dns executable (for verification purposes only)
Both executables located in /app/c_code/ of SMS Data A

III. Installing
1. Install "SMS Data A" app on phone A.
2. Install "SMS Data B" app on phone B.
3. Connect phone A to a computer with ADB (Android Debug Bridge).
4. Push the "hw" executable file to directory "/data/local" on phone A by performing the command "adb push hw /data/local."
  a. File is located in directory /app/c_code/hw in "SMS Data A"
5. You may need to ensure that "hw" has execute permissions on your Android. This can be done by doing "adb shell" to connect to a shell on the phone. Change directory to "/data/local" and then do a "chmod 0777 hw" command.
6. (For verification purposes only) Push the "dns" executable to directory "/data/local" on phone A by performing the command "adb push dns /data/local". You may need to change its permissions to execute properly as done in the previous step.
  a. File is located in directory /app/c_code/dns in "SMS Data A"

IV. Running apps (verifying expected behavior shown in substeps)

1. Start each app on the two phones.

2. On phone A, DISABLE WIFI and make sure 4G DATA IS ENABLED.

3. On phone A, press the "Set Phone Number" button and enter the phone number for phone B (include the area code).

4. On phone B, press the "Set Filter" button and enter the phone number for phone A.

5. (Optional) Verify state of routing table before changing it.
  a. Connect to phone A via adb shell on a computer.
  b. Type "ip route" to show routing table. Observe that the default route sends packets to some outgoing interface, such as wlan0 (WiFi) or rmnet_sdio0 (4G).
  c. In /data/local directory, execute dns executable provided, and give it a valid URL to resolve. This is expected to return promptly with the appropriate IP address and other standard DNS response information.

6. On phone A, press the "Remove Routes" button. This will remove any current default routes from your routing table and replace them with a new default route to "lo", the local loopback interface.
  a. Connect to phone A via adb shell on a computer.
  b. Type "ip route" to show routing table. Observe that the default route now routes packets to the "lo" interface, or local loopback.
  c. In /data/local directory, execute dns executable provided, and give it a valid URL to resolve. This should not ever complete and no IP address should be returned, as we have intercepted outgoing traffic by altering the routing table.

7. On phone A, press the "Start Process" button. This launches the "hw" executable with root permissions. The "hw" executable opens raw sockets to capture both UDP and TCP traffic going in or out of the phone. When it finds a DNS query (i.e. port 53), it forwards the request over another socket to the Android app, SMS Data A, which is listening. This query is then forwarded via SMS to phone B.
  a. Connect to phone A via adb shell on a computer.
  b. Type "ip route" to show routing table. Observe that the default route still routes packets to the "lo" interface, or local loopback.
  c. Now launch "tcpdump -i any" in adb shell. There should be traffic from localhost -> localhost which corresponds to packets being forwarded from the C program to the Android app. There should also start to be incoming packets (i.e. with destination address same as the phone.s IP address) corresponding to DNS responses that have been completed by phone B and sent to phone A.

8. Phone A will now start sending DNS requests to phone B via SMS, and receiving the responses back via SMS. At this time we only have DNS implemented.
  a. In adb shell, navigate to /data/local directory. Launch the .dns. executable. Follow the prompt by entering a valid URL. This will simply send out a DNS query. Our C program will capture it and forward it to the android app on phone A.
  b. Look at the phone A UI. You should see an entry corresponding to the URL you requested in the DNS query.
  c. Phone B will also have information on its UI, but it is difficult to decipher as we want phone B to act as a simple proxy, passing packets from phone A straight to the internet (altering only the IP addresses and ports).
  d. When the request is completed, you will see the entry on phone A update to .Completed. along with the RTT in milliseconds.
  e. To ensure the request completed, look at the output of adb shell where we entered the dns command in the first place. We should now see the resolved IP address for the URL requested.
  f. DNS requests sent by other applications will also be captured and forwarded. This can be tested by doing up to step 7c and then, instead of launching the dns application from data/local, press "Home" on Phone A to navigate to a browser. Visit a URL in the browser. If you return to the SMS-Data A application, you will see the url that the browser requested. Then, by examining tcpdump, you can monitor the output and see the outgoing dns requests and the response.
  g. Exit the application by pressing:
    i. "Stop Process" - This will stop the C code that is intercepting the packets and forwarding them to the Android application.
    ii. "Restore Routes" - This will return the routes to normal. Use .ip route. in adb shell to confirm that the routes have been restored so that Phone A can be used normally. NOTE: If the routes have not been restored, turn off the mobile network and turn it back on again. This should not occur.
    iii. "Exit Application" - The back button is overridden. The application must be explicitly exited.

Example tcpdump output of DNS query for cs.ucla.edu:
[1] 23:19:22.754351 IP 10.176.245.248.47766 > 208.67.222.222.domain: 2692+ A? cs.ucla.edu. (29)
[2] 23:19:22.755724 IP localhost.34567 > localhost.51691: UDP, length 57
...
[3] 23:19:31.904528 IP localhost.51692 > localhost.34567: UDP, length 57
[4] 23:19:31.904894 IP 208.67.222.222.domain > 10.176.245.248.47766: 2692 1/0/0 A 164.67.100.172 (45)

[1] Application issues DNS request for cs.ucla.edu out of source port 47766. DNS request has id 2692. This packet is destined for 208.67.222.222 but is intercepted by the C code.
[2] C code forwards the packet out of port 34567 to the Android application, who is listening to port 51691.
[3] Some time later when the Android application receives the DNS response via SMS, return the DNS Header + Payload to the C program to be processed. Android sends the packet out of port 51692 to the C program which is listening to port 34567.
[4] The C code sets the IP header and UDP headers so that the addresses and ports match that of the original request. Packet it sent out to port 47766 (the port of the application.s original DNS request). The DNS response has ID 2692, matching the original request.
