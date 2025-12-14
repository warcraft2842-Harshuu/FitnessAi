FitnessAI 🏃‍♂️🤖

AI-Powered Fitness Tracking Platform (Microservices Architecture)

📌 Overview

FitnessAI is a full-stack, AI-driven fitness tracking platform built using Spring Boot microservices and a React frontend.
It enables users to securely track physical activities, analyze fitness data, and receive AI-based recommendations focused on performance improvement and safety.

The system is designed with scalability, security, and modularity in mind, leveraging Spring Cloud components such as Eureka, API Gateway, and Config Server, along with OAuth2 authentication using Keycloak.

Frontend (React)
      |
   API Gateway
      |
------------------------------------------------
| User Service | Activity Service | AI Service |
------------------------------------------------
      |
  Config Server
      |
   Eureka Server

🛠️ Tech Stack
### Backend
- Java 17
- Spring Boot
- Spring Cloud (Gateway, Eureka, Config Server)
- Spring Security (OAuth2)
- RESTful APIs
- Maven

### Frontend
- React
- Material UI (MUI)
- Axios
- Redux Toolkit

### Authentication
- Keycloak
- OAuth2 with PKCE

### DevOps / Tools
- Git and GitHub
- Docker (planned)
- Postman


FItnessAI/
├── fitness-app-frontend/   # React frontend
├── gateway/                # API Gateway
├── eureka/                 # Service Discovery
├── configserver/           # Centralized configuration
├── userservice/            # User management
├── activityservice/        # Activity tracking
├── aiservice/              # AI recommendations
└── README.md

🚀 Features
- Secure authentication using OAuth2 (Keycloak)
- Add and track fitness activities
- View activity history
- AI-generated fitness recommendations
- Safety guidelines and improvement suggestions
- Scalable microservices-based architecture

1️⃣ Clone the Repository
git clone https://github.com/warcraft2842-Harshuu/FItnessAI.git
cd FItnessAI

🔐 Authentication Flow
1. User logs in via Keycloak
2. OAuth2 access token is issued
3. Token is passed through the API Gateway
4. Backend services validate the token before processing requests

📌 Future Enhancements

- Docker & Docker Compose support

- CI/CD pipeline using GitHub Actions

- AI model optimization

- Advanced analytics dashboard

- Mobile application

👤 Author

Harsh
Engineering Graduate | System Engineer
Interested in Backend Development, Microservices, and AI

⭐ Support

If you find this project useful, please give it a ⭐ on GitHub.
