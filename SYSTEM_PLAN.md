# DormEase Simple Flowchart

Simple flow for Web and Mobile based on your requested sequence.

## Web Flow (Owner)

~~~mermaid
flowchart TD
    A((Start)) --> B[Open Web App]
    B --> C[Login or Signup]
    C --> D[Owner Dashboard]
    D --> E[View Reservation Requests]
    E --> F[Check Tenant Details]
    F --> G{Confirm Reservation?}
    G -->|No| H[Reject Request]
    H --> I((End))
    G -->|Yes| J[Approve Request]
    J --> K[Tenant Receives Confirmation]
    K --> L[Manage Tenant and Payments]
    L --> M{Terminate Tenant Stay?}
    M -->|No| L
    M -->|Yes| N[Terminate and Archive]
    N --> O[Tenant Gets Termination Update]
    O --> I
~~~

## Mobile Flow (Tenant)

~~~mermaid
flowchart TD
    A((Start)) --> B[Open App]
    B --> C[Login or Signup]
    C --> D[Dashboard]
    D --> E[Browse Dorm]
    E --> F[Check Details]
    F --> G[Reserve]
    G --> H[Wait for Web Owner Confirmation]
    H --> I{Approved?}
    I -->|No| J[Stay in Waiting or Browse Again]
    J --> E
    I -->|Yes| K[Tenant Dashboard]
    K --> L[View Payments]
    L --> M{Terminated by Owner?}
    M -->|No| L
    M -->|Yes| N[See Terminated Status]
    N --> O[Leave Dorm or Reserve Again]
    O --> P((End))
~~~

## Quick Path

1. Mobile tenant path: Open App -> Login/Signup -> Dashboard -> Browse Dorm -> Check Details -> Reserve -> Wait for Confirmation -> Tenant Dashboard -> View Payments.
2. Web owner path: Open Web App -> Login/Signup -> Dashboard -> Review Request -> Confirm or Reject -> Manage Tenant and Payments -> Terminate if needed.
