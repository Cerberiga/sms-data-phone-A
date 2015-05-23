#include <stdio.h>
#include <time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <netinet/udp.h> //Provides declarations for udp header
#include <netinet/tcp.h> //Provides declarations for tcp header
#include <netinet/ip.h>  //Provides declarations for ip header
#include <netinet/if_ether.h>    //For ETH_P_ALL
#include <net/ethernet.h>
#include <ifaddrs.h>
#include <sys/types.h>
#include <signal.h>

/* Begins by creating a datagram socket on 127.0.0.1 which will be used to forward data to
the Android application. Currently, it will print out five timestamps, each one second 
apart to stdout as well as in a packet out of the socket. Afterwards, it creates the raw
data socket. Eventually, we will want to listen to the raw socket and if a message is
received, we will forward the message through the datagram socket.

Compile me with:

arm-linux-gnueabi-gcc -static -march=armv7-a hw.c -o hw

*/

int round_up_32(int num)
{
  return ((num + 3) >> 2)<<2 ;
}

void handle_int()
{
  fflush(NULL);
  exit(1);
}

int main(int argc, char* argv[])
{
  signal(SIGINT, handle_int); 
  if(argc != 2) {
  	printf("Usage: ./hw <interface_name>\n");
  	exit(1);
  }
  int i = 0;
  int sockfd;
  char message[1024];
  struct sockaddr_in saddr, cliaddr;
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

  struct ifaddrs *head;
  getifaddrs(&head);
  struct ifaddrs *ifs = head;
  char my_addr[16];
  char my_netmask[16];
  char locked_addr[16];
  while(ifs)
  {
    if(strcmp(argv[1], ifs->ifa_name) == 0)
    {
      /*printf("%s\n", ifs->ifa_name);
      inet_ntop(AF_INET, &(((struct sockaddr_in *) ifs->ifa_addr)->sin_addr.s_addr), my_addr, 16);
      printf("%s\n", my_addr);
      if(ifs->ifa_netmask)
      {
        inet_ntop(AF_INET, &(((struct sockaddr_in *) ifs->ifa_netmask)->sin_addr.s_addr), my_netmask, 16);
        printf("%s\n", my_netmask);
        if(strcmp("255.255.255.0", my_netmask) == 0)
        {
          strcpy(locked_addr, my_addr);
        }
      }*/
      printf("Interface: %s\n", ifs->ifa_name);
      printf("ifa_addr: %p\n", ifs->ifa_addr);
      if(ifs->ifa_addr)
      {
        inet_ntop(AF_INET, &(((struct sockaddr_in *) ifs->ifa_addr)->sin_addr.s_addr), my_addr, 16);
        printf("Address: %s\n", my_addr);
        if(ifs->ifa_netmask)
        {
          inet_ntop(AF_INET, &(((struct sockaddr_in *) ifs->ifa_netmask)->sin_addr.s_addr), my_netmask, 16);
          printf("%s\n", my_netmask);
          if(strcmp("255.255.255.255", my_netmask) != 0)
          {
            strcpy(locked_addr, my_addr);
            printf("Address found, breaking\n");
            break;
          }
        }
      }
    }
    ifs = ifs->ifa_next;    
  }
  freeifaddrs(head);

  int rec_sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_UDP);
  //int tcp_sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);
  if(rec_sockfd < 0)
  {
    printf("Opening Raw Socket failed due to: %s\n", strerror(errno));
    snprintf(message, 256, "Opening Raw Socket failed due to: %s\n", strerror(errno));
    sendto(sockfd, message, strlen(message), 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
    close(sockfd);
    exit(1);
  }
  else
  {
    snprintf(message, 256, "Raw Socket opened successfully");
    sendto(sockfd, message, strlen(message), 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
  }

  int data_size = 0;
  int eth_size = sizeof(struct ethhdr);
  uint32_t my_ip;
  uint32_t lo;
  inet_pton(AF_INET, "127.0.0.1", &lo);
  inet_pton(AF_INET, "123.123.123.123", &my_ip);
  inet_pton(AF_INET, "169.232.190.243", &my_ip);
  inet_pton(AF_INET, locked_addr, &my_ip);
  printf("%s\n", locked_addr);

  while(1)
  {
    size_t size_of_saddr = sizeof(saddr);
    data_size = recvfrom(rec_sockfd , message , 1024 , 0 , (struct sockaddr *) &saddr , (socklen_t *) &size_of_saddr);
    char addr[16];
    if(data_size > 0)
    {
      struct iphdr *ip_head = (struct iphdr*) (message);
      struct udphdr *udp_head = (struct udphdr*) (message + ip_head->ihl*4);
      //if(1)
      // Outgoing packet
      if((ip_head->saddr == my_ip || ip_head->saddr == lo) && ntohs(udp_head->dest) == 53)
      {
      
      sendto(sockfd, message, data_size, 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
      
      // Logging printouts
      printf("-----------------------\n");
      printf("OUTGOING DNS REQUEST RECEIVED: %d\n", data_size);
      printf("PROTO: %d\n", ip_head->protocol);  
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr);  
      printf("D_IHL: %d\n", ip_head->ihl);
      struct udphdr *udp_head = (struct udphdr*) (message + ip_head->ihl*4);
      printf("%d\n", (int) sizeof(struct udphdr));
      printf("D_PORT: %d\n", ntohs(udp_head->dest));
      printf("S_PORT: %d\n", ntohs(udp_head->source));
      printf("Transport Len: %d\n", ntohs(udp_head->len));
      char * dns_head = (message + ip_head->ihl*4 + sizeof(struct udphdr));
      uint16_t dns_id = ntohs(*((uint16_t *)(dns_head)));
      uint32_t dns_QR_OP = ntohs(*((uint32_t *)(dns_head +2)));
      uint32_t dns_no_qs = ntohs(*((uint32_t *)(dns_head +4)));
      uint32_t dns_test = (*((uint8_t *)(dns_head+3)));
      char* dns_q_name = (dns_head+12);
      printf("DNS ID: %d\n", dns_id);
      printf("DNS QR?: %d\n", (dns_QR_OP &1)); 
      printf("DNS OP?: %d\n", (dns_QR_OP >> 1) & 15); 
      printf("DNS Qs?: %d\n", (dns_no_qs & 0xFFFF)); 
      printf("DNS 0's: %d\n", (dns_test >> 1) & 1); 
      printf("DNS NAME: %s\n", dns_q_name); 
      }
      
      
      
      // Incoming packet
      else if((ip_head->daddr == my_ip || ip_head->daddr == lo)  && 
      			!( ntohs(udp_head->dest) == 51691 && ip_head->daddr == lo && ip_head->saddr == lo))
      {
      sendto(sockfd, message, data_size, 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
      
      // Logging printouts
      printf("-----------------------\n");
      printf("-----------------------\n");
      printf("INCOMING PACKET RECEIVED: %d\n", data_size);
      printf("PROTO: %d\n", ip_head->protocol);  
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr);  
      printf("D_IHL: %d\n", ip_head->ihl);
      struct udphdr *udp_head = (struct udphdr*) (message + ip_head->ihl*4);
      printf("%d\n", (int) sizeof(struct udphdr));
      printf("D_PORT: %d\n", ntohs(udp_head->dest));
      printf("S_PORT: %d\n", ntohs(udp_head->source));
      printf("Transport Len: %d\n", ntohs(udp_head->len));
      char * dns_head = (message + ip_head->ihl*4 + sizeof(struct udphdr));
      uint16_t dns_id = ntohs(*((uint16_t *)(dns_head)));
      uint32_t dns_QR_OP = ntohs(*((uint32_t *)(dns_head +2)));
      uint32_t dns_no_qs = ntohs(*((uint32_t *)(dns_head +4)));
      uint32_t dns_test = (*((uint8_t *)(dns_head+3)));
      char* dns_q_name = (dns_head+12);
      printf("DNS ID: %d\n", dns_id);
      printf("DNS QR?: %d\n", (dns_QR_OP &1)); 
      printf("DNS OP?: %d\n", (dns_QR_OP >> 1) & 15); 
      printf("DNS Qs?: %d\n", (dns_no_qs & 0xFFFF)); 
      printf("DNS As?: %d\n", ((dns_no_qs>>16) & 0xFFFF)); 
      printf("DNS 0's: %d\n", (dns_test >> 1) & 1); 
      printf("DNS NAME: %s\n", dns_q_name); 
      printf("Name length: %d\n", (int) strlen(dns_q_name));
      int name_length = strlen(dns_q_name) + 1; //round_up_32((int) strlen(dns_q_name));
      printf("Rounded name length: %d\n", name_length);
      printf("DNS QUERY TYPE: %d\n", *((short *) (dns_q_name + name_length)));
      printf("DNS QUERY CLASS: %d\n", *((short *) (dns_q_name + name_length + 2)));
      char* dns_r_name = dns_q_name + strlen(dns_q_name) + 4;
      printf("DNS ANSWER NAME, I THINK: %s\n", dns_r_name);
      }
    }
  }

  close(sockfd);
}
