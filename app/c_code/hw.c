#include <stdio.h>
#include <time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>

/* Begins by creating a datagram socket on 127.0.0.1 which will be used to forward data to
the Android application. Currently, it will print out five timestamps, each one second 
apart to stdout as well as in a packet out of the socket. Afterwards, it creates the raw
data socket. Eventually, we will want to listen to the raw socket and if a message is
received, we will forward the message through the datagram socket.

Compile me with:

arm-linux-gnueabi-gcc -static -march=armv7 hw.c -o hw

*/

int main()
{
  int i = 0;
  int sockfd;
  char message[256];
  struct sockaddr_in servaddr, cliaddr;
  socklen_t len;
  int nbytes;
  bzero(&cliaddr, sizeof(cliaddr));
  sockfd=socket(AF_INET, SOCK_DGRAM, 0);
  if(sockfd < 0)
  {
    printf("FAILED\n");
    exit(1);
  }
  cliaddr.sin_family = AF_INET;
  inet_pton(AF_INET, "127.0.0.1", &cliaddr.sin_addr);
  cliaddr.sin_port = htons(51691);
  
  time_t curr_time;
  struct timeval tm;
  for(i = 0; i < 5; i++)
  {
    time(&curr_time);
    long long difft = difftime(curr_time, 0);
    snprintf(message, 256, "%lld\0", difft);
    printf("%s\n", message);
    sendto(sockfd, message, strlen(message), 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
    sleep(1); 
  }


  int rec_sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);
  if(rec_sockfd < 0)
  {
    printf("Opening Raw Socket failed due to: %s\n", strerror(errno));
    snprintf(message, 256, "Opening Raw Socket failed due to: %s\n", strerror(errno));
    sendto(sockfd, message, strlen(message), 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
  }
  else
  {
    snprintf(message, 256, "Raw Socket opened+closed successfully");
    sendto(sockfd, message, strlen(message), 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
    close(rec_sockfd);
  }
  close(sockfd);
}
