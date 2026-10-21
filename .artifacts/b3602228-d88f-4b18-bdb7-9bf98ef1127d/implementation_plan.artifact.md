# Dashboard UI Enhancement Implementation Plan

This plan outlines the changes required to update the Dashboard (HomeFragment) and the Top Bar (DashboardActivity) according to the new design requirements.

## Proposed Changes

### Top Bar (Floating Pill)
- **Goal**: Change the top bar title to display the current date and time in the format "M, September 7 2026".
- **Files to Modify**:
    - [DashboardActivity.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/ui/home/DashboardActivity.java): Implement date formatting and update the `topBarTitle` TextView.

### Dashboard (HomeFragment)
- **Goal**: Reorder the dashboard components and add new sections: Greetings, Announcements, and Recent Activity. Ensure the entire dashboard is scrollable.
- **Order**:
    1. Greetings (Top)
    2. Statistics Cards (2nd)
    3. Announcements (3rd)
    4. Recent Activity (Last)

#### UI Structure ([fragment_home.xml](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/res/layout/fragment_home.xml))
- **Greetings Section**:
    - Add a `TextView` for the personalized greeting: "Good afternoon, [First Name]! Help keep your community clean and healthy. 🌱"
- **Announcements Section**:
    - Add a `CardView` (white background) containing announcement details.
    - Include a "Read more →" button.
- **Recent Activity Section**:
    - Add a `CardView` (white background) listing recent activities (Report Completed, Updated, Submitted).
    - Include a "View All →" button.

#### Logic ([HomeFragment.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/ui/home/HomeFragment.java))
- **Greeting**: Observe the user data from `AuthRepository` to get the `firstName`.
- **Date/Time**: Since the date is needed in the Dashboard top bar, this will be handled in `DashboardActivity`.
- **Recent Activity**: Derive recent activities from the list of reports. For now, we will show a few static examples or top 3 most recent reports.
- **Announcements**: Implement a placeholder UI for announcements since there is no existing API/Model for it.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.

### Manual Verification
- Deploy to the device.
- Verify the top bar displays the correct date format (e.g., "Mon, September 7 2026").
- Verify the greeting message correctly shows the user's first name.
- Verify the order of sections: Greetings -> Stats -> Announcements -> Recent Activity.
- Verify the dashboard is scrollable.
- Verify the white cards for Announcements and Recent Activity are correctly styled.
