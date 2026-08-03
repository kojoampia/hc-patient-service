package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The patient's own details, as shown on the record header and the profile screen.
 */
@Schema(description = "The patient's own details, as shown on the record header and the profile screen.")
@Document(collection = "profile")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Profile implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    @Field("first_name")
    private String firstName;

    @Field("middle_names")
    private String middleNames;

    @Field("last_name")
    private String lastName;

    @Field("membership")
    private String membership;

    @Field("birth_date")
    private LocalDate birthDate;

    @Field("sex")
    private String sex;

    @Field("blood_group")
    private String bloodGroup;

    @Field("mobile_phone")
    private String mobilePhone;

    @Field("phone_number")
    private String phoneNumber;

    @Field("email")
    private String email;

    @Field("card_type")
    private String cardType;

    @Field("card_number")
    private String cardNumber;

    @Field("contacts")
    private String contacts;

    @Field("address")
    private String address;

    @Field("team")
    private String team;

    @Field("image_url")
    private String imageUrl;

    @Field("about")
    private String about;

    @Field("social_handle")
    private String socialHandle;

    @Field("care_angel_name")
    private String careAngelName;

    @Field("care_angel_phone")
    private String careAngelPhone;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Profile id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Profile patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public Profile firstName(String firstName) {
        this.setFirstName(firstName);
        return this;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleNames() {
        return this.middleNames;
    }

    public Profile middleNames(String middleNames) {
        this.setMiddleNames(middleNames);
        return this;
    }

    public void setMiddleNames(String middleNames) {
        this.middleNames = middleNames;
    }

    public String getLastName() {
        return this.lastName;
    }

    public Profile lastName(String lastName) {
        this.setLastName(lastName);
        return this;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMembership() {
        return this.membership;
    }

    public Profile membership(String membership) {
        this.setMembership(membership);
        return this;
    }

    public void setMembership(String membership) {
        this.membership = membership;
    }

    public LocalDate getBirthDate() {
        return this.birthDate;
    }

    public Profile birthDate(LocalDate birthDate) {
        this.setBirthDate(birthDate);
        return this;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getSex() {
        return this.sex;
    }

    public Profile sex(String sex) {
        this.setSex(sex);
        return this;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getBloodGroup() {
        return this.bloodGroup;
    }

    public Profile bloodGroup(String bloodGroup) {
        this.setBloodGroup(bloodGroup);
        return this;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getMobilePhone() {
        return this.mobilePhone;
    }

    public Profile mobilePhone(String mobilePhone) {
        this.setMobilePhone(mobilePhone);
        return this;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public String getPhoneNumber() {
        return this.phoneNumber;
    }

    public Profile phoneNumber(String phoneNumber) {
        this.setPhoneNumber(phoneNumber);
        return this;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return this.email;
    }

    public Profile email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCardType() {
        return this.cardType;
    }

    public Profile cardType(String cardType) {
        this.setCardType(cardType);
        return this;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getCardNumber() {
        return this.cardNumber;
    }

    public Profile cardNumber(String cardNumber) {
        this.setCardNumber(cardNumber);
        return this;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getContacts() {
        return this.contacts;
    }

    public Profile contacts(String contacts) {
        this.setContacts(contacts);
        return this;
    }

    public void setContacts(String contacts) {
        this.contacts = contacts;
    }

    public String getAddress() {
        return this.address;
    }

    public Profile address(String address) {
        this.setAddress(address);
        return this;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTeam() {
        return this.team;
    }

    public Profile team(String team) {
        this.setTeam(team);
        return this;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getImageUrl() {
        return this.imageUrl;
    }

    public Profile imageUrl(String imageUrl) {
        this.setImageUrl(imageUrl);
        return this;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAbout() {
        return this.about;
    }

    public Profile about(String about) {
        this.setAbout(about);
        return this;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getSocialHandle() {
        return this.socialHandle;
    }

    public Profile socialHandle(String socialHandle) {
        this.setSocialHandle(socialHandle);
        return this;
    }

    public void setSocialHandle(String socialHandle) {
        this.socialHandle = socialHandle;
    }

    public String getCareAngelName() {
        return this.careAngelName;
    }

    public Profile careAngelName(String careAngelName) {
        this.setCareAngelName(careAngelName);
        return this;
    }

    public void setCareAngelName(String careAngelName) {
        this.careAngelName = careAngelName;
    }

    public String getCareAngelPhone() {
        return this.careAngelPhone;
    }

    public Profile careAngelPhone(String careAngelPhone) {
        this.setCareAngelPhone(careAngelPhone);
        return this;
    }

    public void setCareAngelPhone(String careAngelPhone) {
        this.careAngelPhone = careAngelPhone;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Profile)) {
            return false;
        }
        return getId() != null && getId().equals(((Profile) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Profile{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", firstName='" + getFirstName() + "'" +
            ", middleNames='" + getMiddleNames() + "'" +
            ", lastName='" + getLastName() + "'" +
            ", membership='" + getMembership() + "'" +
            ", birthDate='" + getBirthDate() + "'" +
            ", sex='" + getSex() + "'" +
            ", bloodGroup='" + getBloodGroup() + "'" +
            ", mobilePhone='" + getMobilePhone() + "'" +
            ", phoneNumber='" + getPhoneNumber() + "'" +
            ", email='" + getEmail() + "'" +
            ", cardType='" + getCardType() + "'" +
            ", cardNumber='" + getCardNumber() + "'" +
            ", contacts='" + getContacts() + "'" +
            ", address='" + getAddress() + "'" +
            ", team='" + getTeam() + "'" +
            ", imageUrl='" + getImageUrl() + "'" +
            ", about='" + getAbout() + "'" +
            ", socialHandle='" + getSocialHandle() + "'" +
            ", careAngelName='" + getCareAngelName() + "'" +
            ", careAngelPhone='" + getCareAngelPhone() + "'" +
            "}";
    }
}
