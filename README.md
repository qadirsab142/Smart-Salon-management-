SMART SALON — COMPLETE JAVA SYSTEM V2

WHAT IS INCLUDED
1) frontend/index.html — your supplied frontend source copied without changing its source code.
2) backend/SalonBackend.java — Java HTTP REST backend, authentication, appointments, profiles, stylists, persistence, admin APIs.
3) admin/AdminDashboard.java — separate black owner/admin desktop dashboard.
4) data/ — runtime data is saved here automatically.
5) run_all.bat — one-click Windows launcher.

IMPORTANT FRONTEND CONNECTION
Your original frontend file is kept unchanged on disk. When you open http://localhost:8080/, the Java backend serves that exact file and appends a small runtime bridge. The bridge connects sign-in, signup, appointments, profile, cancel/reschedule and dashboard data to the Java backend. This avoids editing your supplied frontend source while still making the running site use the backend.

REQUIREMENTS
- JDK 17 or newer (JDK 21 recommended)
- Python 3 for the optional static frontend server only
- VS Code optional

RUN EVERYTHING (WINDOWS)
Double-click run_all.bat.
Then open http://localhost:8080/
Admin login window opens separately.
Admin username: admin
Admin password: Admin@123

MANUAL RUN
Terminal 1:
  javac -d out backend\SalonBackend.java admin\AdminDashboard.java
  java -cp out backend.SalonBackend
Terminal 2:
  java -cp out admin.AdminDashboard
Browser:
  http://localhost:8080/

DO NOT use Live Server for the main connected site. Use http://localhost:8080/ because the backend must serve the frontend plus the runtime bridge.

USER TEST
- Open site.
- Click Sign In.
- Existing test account: alex@example.com / User@123
- Or create a new account.
- Book a service, date, time and stylist/recommendation.
- Confirmation is stored in Java backend data.
- Dashboard loads appointments from backend.
- Cancel/reschedule/profile save are backend operations.

ADMIN TEST
- Login with admin / Admin@123.
- Overview shows users, appointments, active bookings, completed, cancelled, stylists and revenue.
- Appointments tab: view all customers/bookings, mark completed or cancelled.
- Customers tab: view registered users.
- Stylists tab: view stylists and toggle active status.
- Transactions tab: view revenue-generating appointments.

DATA
The system uses Java object serialization in data/salon-store.bin. This is intentionally dependency-free for a university/VS Code setup. It persists users, stylists, appointments and transactions between runs.

SECURITY NOTE
Admin credentials are seeded for local development/demo. Before real deployment, change the password and move secrets/configuration outside source code. Use HTTPS and a production database/auth system.

CURRENT KNOWN LIMITATIONS / NEXT PRODUCTION IMPROVEMENTS
- Replace file serialization with MySQL/PostgreSQL.
- Use proper JWT/session storage with expiry and HTTPS.
- Add full admin CRUD for services, prices, users and stylists.
- Add real availability/slot collision validation.
- Add DSA allocation/queue engine as a dedicated service (priority queue + greedy allocation + queue metrics).
- Add email/WhatsApp notification service with the real salon number.
- Add server-side validation, audit logs and role permissions.

DSA READY POINT
The backend is structured so the appointment allocation layer can be expanded with Queue/PriorityQueue, searching and sorting without redesigning the public frontend.
