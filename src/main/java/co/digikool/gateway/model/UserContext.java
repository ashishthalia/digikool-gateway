package co.digikool.gateway.model;

import java.util.List;

public class UserContext {
    private String userId;
    private String schoolId;
    private List<String> permissions;
    private String userEmail;
    private String username;
    private String firstName;
    
    public UserContext() {}
    
    public UserContext(String userId, String schoolId, List<String> permissions, 
                      String userEmail, String username, String firstName) {
        this.userId = userId;
        this.schoolId = schoolId;
        this.permissions = permissions;
        this.userEmail = userEmail;
        this.username = username;
        this.firstName = firstName;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getSchoolId() {
        return schoolId;
    }
    
    public void setSchoolId(String schoolId) {
        this.schoolId = schoolId;
    }
    
    public List<String> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
    
    public String getUserEmail() {
        return userEmail;
    }
    
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    @Override
    public String toString() {
        return "UserContext{" +
                "userId='" + userId + '\'' +
                ", schoolId='" + schoolId + '\'' +
                ", permissions=" + permissions +
                ", userEmail='" + userEmail + '\'' +
                ", username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                '}';
    }
}
