# Instance Maintainer Guide

[Go back to main Documentation page](/docs/README.md)

This page will guide you through the configuration of a working EpiLink instance.

## Deployment methods

There are (or, rather, will be) several ways of deploying EpiLink:

- All-in-one package (includes back-end, front-end and JRE)
- Separate packages (one for back-end, one for front-end, requires a JRE to be installed) 

All-in-one is recommended for most use cases, although it is not necessarily the most secure or fastest one.

## Running

EpiLink can be ran with a few arguments. Run `path/to/epilink -h` for help.

EpiLink should typically be ran like so: `path/to/epilink path/to/config/file/epilink_config.yaml`

## Configuration

The most important part of EpiLink is the configuration file.

The reference configuration has all the information you need. This page has more details.

The standard name for the configuration file is `epilink_config.yaml`, although this is just a convention and you can
use any name you like.

### General settings

```yaml
name: My EpiLink Instance
db: epilink.db
```

* `name`: This is the name of your instance. This name is public and should describe your instance. For example "MyAmazingOrg Account Checker".

* `db`: This is the location of the SQLite database. Use a full, absolute path instead of a relative path just to be on the safe side.

### HTTP Server Settings

```yaml
server:
  port: 9090
  sessionDuration: 2592000000
  frontendUrl: ~
```

* `port`: The port on which the back-end will be served
* `sessionDuration`: Unused at the moment.
* `frontendUrl`: The URL of the front-end *WITH A TRAILING SLASH* (e.g. `https://myfrontend.com/`), or `~` if the front-end is unknown or you are using the all-in-one packages (i.e. the front-end is bundled with the back-end).


### Credentials

```yaml
tokens:
    discordToken: ~
    discordOAuthClientId: ~
    discordOAuthSecret: ~
    msftOAuthClientId: ~
    msftOAuthSecret: ~
    msftTenant: common
```

The first step is to set up the credential EpiLink will use to contact Microsoft and Discord APIs.

#### Discord

Create an application at [Discord's developer portal](https://discordapp.com/developers/applications/). You will also need to create a bot for the application (check the Bot section).

The Bot section on Discord's developer portal will determine what the application looks like on Discord. Take some time to customize its logo and username.

| Name in the Developer Portal          | Name in the config file |
| ------------------------------------- | ----------------------- |
| General Information -> Client ID      | `discordOAuthClientId`  |
| General Information -> Client Secret  | `discordOAuthSecret`    |
| Bot -> Token                          | `discordToken`          |

{TODO Add redirect_uri information}

#### Microsoft

##### Choosing an account and tenants

Before starting, you must question *where* your app will live. If your app will live inside a company or school, you should do this entire procedure from your company or school account, and, if there is only one Azure tenant, choose the "account in my organization" option. EpiLink will work fine with just your regular Microsoft account, but this is not recommended.

EpiLink allows you to define a tenant in the configuration file directly, so just choose what makes sense for your use case. 

##### Registering EpiLink

Go to the [Azure portal](https://portal.azure.com) and connect to your account ([which one?](#choosing-an-account-and-tenants)). Go to the Azure Active Directory view, then "App Registrations" and "New registration".

Carefully choose the "Supported account types field" ([help](#choosing-an-account-and-tenants)).

The registering process can take some time, just be patient.

Once done, you will be redirected to your app's page.

You will need to create a secret manually, as Azure AD does not create one for you automatically. Simply go to Certificates & Secrets and click on New client secret. Note that the secret will not be visible once you leave the page, so copy it then and not later!

| Name in Azure AD Application page        | Name in the config file |
| ---------------------------------------  | ----------------------- |
| Overview -> Application (client) ID      | `msftOAuthClientId`     |
| Certificates & Secrets -> Client secrets | `msftOAuthSecret`       |

{TODO Add redirect_uri information}

##### Tenant

`msftTenant` can take a few different values:
 
* `common` to accept any Microsoft account (personal, business/school account)
* `consumers` to accept only personal Microsoft accounts
* `organizations` to accept only business/school accounts
* Your tenant's ID to accept only accounts from your tenant. You can get it by connecting to the Azure portal with your work account and going to `?` -> Show Diagnostics. In the JSON file, you will find a list of tenants, simply pick the ID of the one you want.v

