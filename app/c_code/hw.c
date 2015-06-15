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
#include <netinet/in.h>
#include <net/ethernet.h>
#include <ifaddrs.h>
#include <sys/types.h>
#include <signal.h>
#include <pthread.h>
#include <errno.h>	

/* Begins by creating a datagram socket on 127.0.0.1 which will be used to forward data to
the Android application. Currently, it will print out five timestamps, each one second 
apart to stdout as well as in a packet out of the socket. Afterwards, it creates the raw
data socket. Eventually, we will want to listen to the raw socket and if a message is
received, we will forward the message through the datagram socket.

Compile me with:

arm-linux-gnueabi-gcc -static -march=armv7-a  hw.c -o hw

*/

// Socket to connect with Android app
int sockfd;
struct sockaddr_in saddr1, saddr2, cliaddr;
char locked_addr[16];
pthread_mutex_t lock;

//function prototypes for threaded functions
void *capture_tcp_traffic(void *);
void *capture_udp_traffic(void *);
void *capture_android_message(void *v);
void printDataAsHex(char*, int);

int round_up_32(int num)
{
  return ((num + 3) >> 2)<<2 ;
}

void handle_int()
{
  fflush(NULL);
  exit(1);
}

int is_le;

int main(int argc, char* argv[])
{
  int tester = 1;
  if(*((char*)&tester) == 1)
  {
    is_le = 1;
  }
  else
  {
    is_le = 0;
  }

  if(is_le)
  {
    printf("IS LITTLE ENDIAN\n");
  }
  else
  {
    printf("IS BIG ENDIAN\n");
  }
  signal(SIGINT, handle_int); 
  if(argc != 2) {
  	printf("Usage: ./hw <interface_name>\n");
  	exit(1);
  }
  int i = 0;
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
  //char locked_addr[16];
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
printf("Address: %s\n", my_addr);
            break;
          }
        }
      }
    }
    ifs = ifs->ifa_next;    
  }
  freeifaddrs(head);
  
  /*************** MULTI-THREADING ************************/
  // Create lock for printouts
  if (pthread_mutex_init(&lock, NULL) != 0)
    {
        printf("\n mutex init failed\n");
        return 1;
    }
    


  pthread_t tcp_thread, udp_thread, android_thread;
  pthread_create(&tcp_thread, NULL, &capture_tcp_traffic, NULL);
  pthread_create(&udp_thread, NULL, &capture_udp_traffic, NULL);
  pthread_create(&android_thread, NULL, &capture_android_message, NULL);
  
  pthread_join( tcp_thread , NULL); 
  pthread_join( udp_thread, NULL);
  pthread_join( android_thread, NULL);
  pthread_mutex_destroy(&lock);
  close(sockfd);
}

char * toString(char * s)
{
  int len = strlen(s);
  char* ret = (char *) malloc(len);
  memcpy(ret, s+1, len-1);
  int i = 0;
  while(s[i] != 0)
  {
    int offset = (unsigned char) s[i];
    if(i != 0)
    {
      ret[i-1] = '.';
    }
    i += offset + 1;
  } 
  return ret;
}


/***********************************************************/
/****************CAPTURE UDP******************************/
/***********************************************************/
void *capture_udp_traffic(void *v) {
  int counter = 0;
  char message[1024];
  //char locked_addr[16];
  int rec_sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_UDP);
  if(rec_sockfd < 0)
  {
    printf("Opening Raw UDP Socket failed due to: %s\n", strerror(errno));
    snprintf(message, 256, "Opening Raw Socket failed due to: %s\n", strerror(errno));
    sendto(sockfd, message, strlen(message), 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
    close(sockfd);
    exit(1);
  }
  else
  {
    snprintf(message, 256, "Raw UDP Socket opened successfully");
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
  //printf("LOCKED ADDR: %s\n", locked_addr);
  
  while(1)
  {
   size_t size_of_saddr1 = sizeof(saddr1);
   data_size = recvfrom(rec_sockfd , message , 1024 , 0 , (struct sockaddr *) &saddr1 , (socklen_t *) &size_of_saddr1);

	char addr[16];
    if(data_size > 0)
    {
      struct iphdr *ip_head = (struct iphdr*) (message);
      struct udphdr *udp_head = (struct udphdr*) (message + ip_head->ihl*4);
      /*printf("BLABLAH\n");
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr); 
      printf("%d\n", my_ip);
      printf("%d\n", ip_head->saddr);
      inet_ntop(AF_INET, &my_ip, addr, 32);
      printf("MY_IP: %s\n", addr);
 */
      // Outgoing DNS packet
      if((ip_head->saddr == my_ip || ip_head->saddr == lo) && ntohs(udp_head->dest) == 53)
      {
      // Logging printouts
      printf("-----------------------\n");
      printf("OUTGOING UDP DNS REQUEST RECEIVED: %d\n", data_size);
      printf("PROTO: %d\n", ip_head->protocol);  
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr);  
      //printf("D_IHL: %d\n", ip_head->ihl);
      struct udphdr *udp_head = (struct udphdr*) (message + ip_head->ihl*4);
      //printf("%d\n", (int) sizeof(struct udphdr));
      printf("D_PORT: %d\n", ntohs(udp_head->dest));
      printf("S_PORT: %d\n", ntohs(udp_head->source));
      printf("Transport Len: %d\n", ntohs(udp_head->len));
      char * dns_head = (message + ip_head->ihl*4 + sizeof(struct udphdr));
      uint16_t dns_id = ntohs(*((uint16_t *)(dns_head)));
      uint32_t dns_QR_OP = ntohs(*((uint32_t *)(dns_head +2)));
      uint32_t dns_no_qs = ntohs(*((uint32_t *)(dns_head +4)));
      uint32_t dns_test = (*((uint8_t *)(dns_head+3)));
      char* dns_q_name = (dns_head+12);
      /*
      printf("DNS ID: %d\n", dns_id);
      printf("DNS QR?: %d\n", (dns_QR_OP &1)); 
      printf("DNS OP?: %d\n", (dns_QR_OP >> 1) & 15); 
      printf("DNS Qs?: %d\n", (dns_no_qs & 0xFFFF)); 
      printf("DNS 0's: %d\n", (dns_test >> 1) & 1); 
      */
      printf("DNS NAME: %s\n", dns_q_name);
      printDataAsHex(message, data_size);

      //char name[] = "cs.ucla.edu";
      char selfhelp[] = "selfhelp.geo.t-mobile.com";
      /*char selfhelp[27];
      selfhelp[0] = 0x8;
      memcpy(selfhelp + 1, blacklist + 1, 8);
      selfhelp[9] = 0x3;
      memcpy(selfhelp + 10, blacklist + 10, 3);
      selfhelp[13] = 0x8;
      memcpy(selfhelp + 14, blacklist + 14, 8);
      selfhelp[22] = 0x3;
      memcpy(selfhelp + 23, blacklist + 32, 3);
      selfhelp[26] = 0;
        */    
      char * val = toString(dns_q_name);
      printf("\n%s\n", val);
      /*if(strcmp(val, "cs.ucla.edu") == 0)
      {
        printf("SAME\n");
      }*/

      char name_from_packet[12];
      name_from_packet[11] = '\0';
      memcpy(name_from_packet, dns_q_name + 1, 11); 
      name_from_packet[2] = '.';
      name_from_packet[7] = '.';
      //printf("%d\n", strcmp(name_from_packet, name));
      //printf("%d\n", strcmp(name_from_packet, "name"));

      size_t name_size = strlen(dns_q_name);

      //if(strcmp(name_from_packet, name) == 0 && *(dns_q_name + name_size + 2) != 0x1c) {
      if(strcmp(val, selfhelp) != 0) {
      	sendto(sockfd, message, data_size, 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
       }
       else
       {
         printf("------OMITTING SELFHELP------\n");
       }
      }
      

      // Incoming packet
      /*else if((ip_head->daddr == my_ip || ip_head->daddr == lo)  && 
      			!( ntohs(udp_head->dest) == 51691 && ip_head->daddr == lo && ip_head->saddr == lo))
      {
      
      // Logging printouts
      printf("-----------------------\n");
      printf("-----------------------\n");
      printf("INCOMING UDP PACKET RECEIVED: %d\n", data_size);
      printf("PROTO: %d\n", ip_head->protocol);  
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr);  
      //printf("D_IHL: %d\n", ip_head->ihl);
      struct udphdr *udp_head = (struct udphdr*) (message + ip_head->ihl*4);
      //printf("%d\n", (int) sizeof(struct udphdr));
      printf("D_PORT: %d\n", ntohs(udp_head->dest));
      printf("S_PORT: %d\n", ntohs(udp_head->source));
      printf("Transport Len: %d\n", ntohs(udp_head->len));
      char * dns_head = (message + ip_head->ihl*4 + sizeof(struct udphdr));
      uint16_t dns_id = ntohs(*((uint16_t *)(dns_head)));
      uint32_t dns_QR_OP = ntohs(*((uint32_t *)(dns_head +2)));
      uint32_t dns_no_qs = ntohs(*((uint32_t *)(dns_head +4)));
      uint32_t dns_test = (*((uint8_t *)(dns_head+3)));
      char* dns_q_name = (dns_head+12);
      
      //printf("DNS ID: %d\n", dns_id);
      //printf("DNS QR?: %d\n", (dns_QR_OP &1)); 
      //printf("DNS OP?: %d\n", (dns_QR_OP >> 1) & 15); 
      //printf("DNS Qs?: %d\n", (dns_no_qs & 0xFFFF)); 
      //printf("DNS As?: %d\n", ((dns_no_qs>>16) & 0xFFFF)); 
      //printf("DNS 0's: %d\n", (dns_test >> 1) & 1); 
      
      printf("DNS NAME: %s\n", dns_q_name); 
      printDataAsHex(message, data_size);
      
      //printf("Name length: %d\n", (int) strlen(dns_q_name));
      //int name_length = strlen(dns_q_name) + 1; //round_up_32((int) strlen(dns_q_name));
      //printf("Rounded name length: %d\n", name_length);
      //printf("DNS QUERY TYPE: %d\n", *((short *) (dns_q_name + name_length)));
      //printf("DNS QUERY CLASS: %d\n", *((short *) (dns_q_name + name_length + 2)));
      //char* dns_r_name = dns_q_name + strlen(dns_q_name) + 4;
      //printf("DNS ANSWER NAME, I THINK: %s\n", dns_r_name);
      
      if(ntohs(udp_head->dest) == 53)
      {
        sendto(sockfd, message, data_size, 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
      }
      }*/
    }
  }
}

/**********************************************************/
/***********************CAPTURE TCP *********************/
/**********************************************************/
// Function that captures TCP traffic on 127.0.0.1
// Call this function on new thread to run asynchronously with the UDP socket.
void *capture_tcp_traffic(void *v) {
  int counter = 0;
  char message[1024];
  //char locked_addr[16];
  int tcp_sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);
  
  if(tcp_sockfd < 0) {
    printf("Opening TCP Raw Socket failed due to: %s\n", strerror(errno));
    snprintf(message, 256, "Opening Raw Socket failed due to: %s\n", strerror(errno));
    sendto(sockfd, message, strlen(message), 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
    close(sockfd);
    exit(1);
  }
  else {
    snprintf(message, 256, "Raw TCP Socket opened successfully");
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
    size_t size_of_saddr2 = sizeof(saddr2);
    data_size = recvfrom(tcp_sockfd , message , 1024 , 0 , (struct sockaddr *) &saddr2 , (socklen_t *) &size_of_saddr2);
    char addr[16];
    if(data_size > 0)
    {
      struct iphdr *ip_head = (struct iphdr*) (message);
      struct tcphdr *tcp_head = (struct tcphdr*) (message + ip_head->ihl*4);
      /*printf("BLABLAH\n");
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr); 
      printf("%d\n", my_ip);
      printf("%d\n", ip_head->saddr);
      inet_ntop(AF_INET, &my_ip, addr, 32);
      printf("MY_IP: %s\n", addr);*/
      // Outgoing packet
      if((ip_head->saddr == my_ip || ip_head->saddr == lo) ) //&& ntohs(udp_head->dest) == 53)
      {
        // Logging printouts
      printf("-----------------------\n");
      printf("OUTGOING TCP PACKET RECEIVED: %d\n", data_size);
      printf("PROTO: %d\n", ip_head->protocol);  
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr);  
      //printf("D_IHL: %d\n", ip_head->ihl);
      //printf("SIZEOF_TCPHDR: %d\n", (int) sizeof(struct tcphdr));
      printf("D_PORT: %d\n", ntohs(tcp_head->dest));
      printf("S_PORT: %d\n", ntohs(tcp_head->source));
      
      printDataAsHex(message, data_size);
      
      /*
      //printf("SEQ#: %d\n", ntohs(tcp_head->th_seq));
      printf("SEQ#: %d\n", ntohs(tcp_head->seq));
      //printf("ACK#: %d\n", ntohs(tcp_head->th_ack));
      printf("ACK#: %d\n", ntohs(tcp_head->ack_seq));
      */
      }
      else if((ip_head->daddr == my_ip || ip_head->daddr == lo) ) //&& !( ntohs(tcp_head->dest) == 51691 && ip_head->daddr == lo && ip_head->saddr == lo))
      {
      printf("INCOMING TCP PACKET RECEIVED: %d\n", data_size);
      printf("PROTO: %d\n", ip_head->protocol);  
      inet_ntop(AF_INET, &ip_head->saddr, addr, 32);
      printf("S_IP: %s\n", addr);  
      inet_ntop(AF_INET, &ip_head->daddr, addr, 32);
      printf("D_IP: %s\n", addr);  
      //printf("D_IHL: %d\n", ip_head->ihl);
      //printf("%d\n", (int) sizeof(struct tcphdr));
      printf("D_PORT: %d\n", ntohs(tcp_head->dest));
      printf("S_PORT: %d\n", ntohs(tcp_head->source));
      
      printDataAsHex(message, data_size);
    
      /*
      //printf("SEQ#: %d\n", ntohs(tcp_head->th_seq));
      printf("SEQ#: %d\n", ntohs(tcp_head->seq));
      //printf("ACK#: %d\n", ntohs(tcp_head->th_ack));
      printf("ACK#: %d\n", ntohs(tcp_head->ack_seq));
      */
      }
      }
      
  }
  
}

void printDataAsHex(char* data, int length) {
	pthread_mutex_lock(&lock);
	printf("Data (length %d):\n", length);
	int i = 0;
	while(i < length)
	{
		if(i%4 == 0)
		{
			printf("\n");
		}
		printf("%02x ", (unsigned char) data[i]);
		i++;
	}
	pthread_mutex_unlock(&lock);
}

// Needed to compute checksum for UDP header
struct pseudo_header
{
    u_int32_t source_address;
    u_int32_t dest_address;
    u_int8_t placeholder;
    u_int8_t protocol;
    u_int16_t udp_length;
};


/*
    Generic checksum calculation function
*/
unsigned short be_csum(unsigned short *ptr,int nbytes) 
{
    register long sum;
    unsigned short oddbyte;
    register short answer;
 
    sum=0;
    while(nbytes>1) {
        sum+=*ptr++;
        nbytes-=2;
    }
    if(nbytes==1) {
        oddbyte=0;
        *((u_char*)&oddbyte)=*(u_char*)ptr;
        sum+=oddbyte;
    }
 
    sum = (sum>>16)+(sum & 0xffff);
    sum = sum + (sum>>16);
    answer=(short)~sum;
     
    return(answer);
}
 
unsigned short csum(unsigned char *ptr, int nbytes)
{
    unsigned long sum;
    unsigned char byte1, byte2;

    sum=0;
    while(nbytes>1) {
        byte1 = *ptr++;
        byte2 = *ptr++;
        sum += ((unsigned short)byte1<<8) + byte2;
        nbytes -= 2;
    }

    // If odd number of bytes, last one must be shifted left 8 bits 
    if(nbytes==1) {
        byte1 = *ptr++;
        sum += ((unsigned short)byte1<<8);
        nbytes--;
    }

        // Add any overflow back onto sum
    sum = (sum>>16)+(sum & 0xffff);
    sum = (sum>>16)+(sum & 0xffff);

    return ~(short)sum;
}

// Function that listens to traffic on 127.0.0.1, port 34567
// Call this function on new thread to run asynchronously with the socket..
void *capture_android_message(void *v) {
   struct sockaddr_in servaddr, recvaddr;
   char recv_data[1024];
   
   bzero(&servaddr,sizeof(servaddr));	 // puts all zeros in servaddr
   servaddr.sin_family = AF_INET;
   servaddr.sin_addr.s_addr=htonl(INADDR_ANY);
   servaddr.sin_port=htons(34567);
   //int newsockfd;
   //sockfd=socket(AF_INET, SOCK_DGRAM, 0);
   int bind_result = bind(sockfd,(struct sockaddr *)&servaddr,sizeof(servaddr));  // Bind port 34567 to this socket
   
   // Open new raw socket to forward data to original application
  //char locked_addr[16];
  int final_sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_UDP);
  if(final_sockfd < 0) {
		printf("Opening Raw UDP Socket (for C -> end-application communication) failed due to: %s\n", strerror(errno));
		close(sockfd);
		exit(1);
  } else {
    	printf("Raw UDP Socket (for C -> end-application communication) opened successfully\n");
  }
  
    int ip_hdr_yes = 1;
    if(setsockopt(final_sockfd, IPPROTO_IP, IP_HDRINCL, &ip_hdr_yes, sizeof(ip_hdr_yes)) < 0)
    {
      perror("ERROR SETTING IP_HDRINCL\n");
      exit(1);
    }
   // Receive DNS responses from android app
	while(1)
	{
      socklen_t recv_addr_len = sizeof(recvaddr);
      int bytes_recvd = recvfrom(sockfd,recv_data,1024,0,(struct sockaddr *)&recvaddr,&recv_addr_len);
      //sendto(sockfd,recv_data,bytes_recvd,0,(struct sockaddr *)&recvaddr,sizeof(recvaddr));
      printf("-------------------------------------------------------\n");
      recv_data[bytes_recvd] = 0; // place null character to end the string, for printing purposes
      printf("Received the following:\n");
      printf("%s",recv_data);
      printf("-------------------------------------------------------\n");
      
      
      //Datagram to represent the packet to send out to application
      char datagram[4096] , *data , *pseudogram;
     
    memset (datagram, 0, 4096); //zero out the packet buffer
    
    union ip_union {
    	char bytes[4];
    	uint32_t num;
    } source_ip, dest_ip;
    
    char sip2[5];
    
     sip2[0] = recv_data[3];
     sip2[1] = recv_data[2];
     sip2[2] = recv_data[1];
     sip2[3] = recv_data[0];
     sip2[4] = 0;
     // Extract source/dest IP from message
     source_ip.bytes[0] = recv_data[3];
     source_ip.bytes[1] = recv_data[2];
     source_ip.bytes[2] = recv_data[1];
     source_ip.bytes[3] = recv_data[0];
     dest_ip.bytes[0] = recv_data[7];
     dest_ip.bytes[1] = recv_data[6];
     dest_ip.bytes[2] = recv_data[5];
     dest_ip.bytes[3] = recv_data[4];
     
     union port_union {
    	char bytes[2];
    	uint16_t num;
    } source_port, dest_port;
    
     // Extract source/dest port from message
     source_port.bytes[0] = recv_data[9];
     source_port.bytes[1] = recv_data[8];
     dest_port.bytes[0] = recv_data[11];
     dest_port.bytes[1] = recv_data[10];
     
    //IP header
    struct iphdr *iph = (struct iphdr *) datagram;
     
    //UDP header
    struct udphdr *udph = (struct udphdr *) (datagram + sizeof (struct ip));
     
     //Data part. Copy the received data to the new datagram packet.
    data = datagram + sizeof(struct iphdr) + sizeof(struct udphdr);
    memcpy(data, recv_data+12, bytes_recvd-12);
    
    struct sockaddr_in sin;
    struct pseudo_header psh;
    
    sin.sin_family = AF_INET;
    sin.sin_port = htons(source_port.num);
    sin.sin_addr.s_addr = source_ip.num;
    //printf("%d\n", source_ip.num);
    //Fill in the IP Header
    iph->ihl = 5;				// Header length
    iph->version = 4;		// IPv4
    iph->tos = 0;				// Type of Service
    //iph->tot_len = sizeof (struct iphdr) + sizeof (struct udphdr) + strlen(data);
    iph->tot_len = sizeof (struct iphdr) + sizeof (struct udphdr) + bytes_recvd - 12;
    iph->id = htonl (54321); // Id of this packet. Only used for fragmenting Datagrams
    iph->frag_off = 0;			// Fragment offset
    iph->ttl = 255;				// Time To Live
    iph->protocol = IPPROTO_UDP;	// Designates wrapping a UDP packet
    iph->check = 0;      //Set to 0 before calculating checksum
    //iph->saddr = inet_addr ( source_ip.bytes );    // Source IP
    iph->saddr = source_ip.num;//inet_addr ( source_ip.bytes );    // Source IP
    iph->daddr = dest_ip.num;//inet_addr( dest_ip.bytes );			// Dest IP
     
    //Ip checksum
    //iph->check = csum ((unsigned short *) datagram, iph->tot_len);
    iph->check = csum ((unsigned char *) datagram, iph->tot_len);
     
    //UDP header
    udph->source = source_port.num;//htons (source_port.num);
    udph->dest = dest_port.num;//htons (dest_port.num);
    udph->len = htons(8 + bytes_recvd - 12); // UDP header size
    udph->check = 0; //leave checksum 0 now, filled later by pseudo header
     /****************************************************/
     /***********Not sure how the below code works *****/
     /***************************************************/
     
    //Now the UDP checksum using the pseudo header
    psh.source_address = source_ip.num;//inet_addr( source_ip.bytes );
    psh.dest_address = dest_ip.num;//sin.sin_addr.s_addr;
    psh.placeholder = 0;
    psh.protocol = IPPROTO_UDP;
    psh.udp_length = htons(sizeof(struct udphdr) + bytes_recvd - 12);//strlen(data) );

    
    int psize = sizeof(struct pseudo_header) + sizeof(struct udphdr) + bytes_recvd - 12;//strlen(data);
    pseudogram = malloc(psize);
     
    memcpy(pseudogram , (char*) &psh , sizeof (struct pseudo_header));
    memcpy(pseudogram + sizeof(struct pseudo_header) , udph , sizeof(struct udphdr) + bytes_recvd - 12);//strlen(data));
    

    printf(">>>>>>>>>>>>>>>>PSEUDOGRAM<<<<<<<<<<<\n");
    printDataAsHex((char*) &pseudogram, 12 + 8 + bytes_recvd - 12);
    printf(">>>>>>>>>>>>>>>>PSEUDOGRAM<<<<<<<<<<<\n");
     
 
    //udph->check = csum( (unsigned char*) pseudogram , psize);
    udph->check = htons(csum( (unsigned char*) pseudogram , psize));
//udph->check = 0;
    printf("-------------SENDING to application----------\n");
    int i = 0;
    
    printDataAsHex(datagram, bytes_recvd - 12 + sizeof(struct udphdr) + sizeof(struct iphdr));
    
    printf("\n");
		//Send the packet
		if (sendto (final_sockfd, datagram, iph->tot_len ,  0, (struct sockaddr *) &sin, sizeof (sin)) < 0)
		{
			perror("sendto failed");
		}
		//Data send successfully
		else
		{
			printf ("Packet Send. Length : %d \n" , iph->tot_len);
		}
 
    /*int nsockfd = socket(AF_INET, SOCK_DGRAM, 0);
    struct sockaddr_in appaddr;
    bzero(&appaddr, sizeof(appaddr));
    appaddr.sin_family = AF_INET;
    appaddr.sin_addr.s_addr = iph->daddr;
    appaddr.sin_port= udph->dest;
    int a = 1;
    if(setsockopt(nsockfd, SOL_SOCKET, SO_REUSEADDR, &a, sizeof(a)) < 0)
    {
      printf("DOESN'T WORK\n");
      exit(1);
    }
    struct sockaddr_in bindaddr;
    bzero(&bindaddr, sizeof(bindaddr));
    bindaddr.sin_family = AF_INET;
    //bindaddr.sin_addr.s_addr=iph->saddr;
    bindaddr.sin_addr.s_addr=inet_addr("123.123.123.123");
    bindaddr.sin_port=htons(53);
    if(bind(nsockfd, (struct sockaddr*) &bindaddr, sizeof(bindaddr)) < 0)
    {
      
      printf("ALSO DOESN'T WORK\n");
      printf("ERR: %s\n", strerror(errno));
      exit(1);
    }
    sendto(nsockfd, datagram+28, bytes_recvd - 12, 0, (struct sockaddr *) &appaddr, sizeof(appaddr));*/
    //return 0;
   }
}
