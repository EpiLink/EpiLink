# Identity Providers

Identity Providers (formally known as OpenID Providers or OPs in the OpenID Connect standard) are services which provide log-in and authentication services.

You can use any OpenID Connect compliant service for EpiLink. This page will describe the recommended configuration for the most popular ones.

You can set the Identity Provider [in the `idProvider` section of the configuration file](TODO).

## Discovery process

Identity providers are discovered using a single URL, sometimes called the "Issuer" or "Authority" URL. EpiLink will use this URL to determine the location of the OpenID Connect configuration file, which in turn provides all of the information EpiLink needs to do its magic. The location of the configuration file is the Issuer/Authority URL followed by `/.well-known/openid-configuration`.

Note that OpenID Providers (aka Identity Providers) need to be compatible with the [Authorization Code flow](https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth) defined in the OpenID Connect standard.

## Popular Identity Providers

### Microsoft

```yaml
idProvider:
  url: https://login.microsoftonline.com/TENANT/v2.0
  name: Microsoft
  icon: { file: ms_icon.svg, contentType: "image/svg+xml" }
  microsoftBackwardsCompatibility: false #optional
```

Replace TENANT in the URL by your [Azure tenant](#choosing-an-account-and-tenant).

!> **If you were using EpiLink before version 0.5**, you **MUST** set `microsoftBackwardsCompatibility` to true, otherwise EpiLink will not recognize users that registered before you updated. <br>**If you started using EpiLink with version 0.5 or later**, **DO NOT** enable this option, you can remove the line entirely.

You can find the `ms_icon.svg` file [on this page](https://docs.microsoft.com/en-us/azure/active-directory/develop/howto-add-branding-in-azure-ad-apps#visual-guidance-for-app-acquisition:~:text=To%20download%20the%20official%20Microsoft%20logo,then%20save%20it%20to%20your%20computer.). Place it next to your configuration file.

The following is a more in-depth description of how to configure everything from the Microsoft side:

#### Choosing an account and tenant

Before using Microsoft as an Identity Provider, you must determine *where* your app will live. If your app will live inside a company or school, you should do this entire procedure from your company or school account, and, if there is only one Azure tenant, choose the "account in my organization" option. EpiLink will work fine with just your regular Microsoft account, but this is not recommended.

EpiLink allows you to define a tenant in the configuration file directly, so just choose what makes sense for your use case. 

* `common` to accept any Microsoft account (personal, business/school account)
* `consumers` to accept only personal Microsoft accounts
* `organizations` to accept only business/school accounts
* Your tenant's ID to accept only accounts from your tenant. You can get it by connecting to the Azure portal with your work account and going to `?` -> Show Diagnostics. In the JSON file, you will find a list of tenants, simply pick the ID of the one you want.

?> If you need to use multiple tenants or `common`, `consumers` or `organizations`, and cannot guarantee identities by just having a tenant in place, you can validate e-mail addresses using rulebooks. [Read this for more information](Rulebooks.md#e-mail-validation).

#### Registering EpiLink

Go to the [Azure portal](https://portal.azure.com) and log in with your Microsoft account ([which one?](#choosing-an-account-and-tenants)). Go to the Azure Active Directory view, then "App Registrations" and "New registration".

Carefully choose the "Supported account types field" ([help](#choosing-an-account-and-tenants)). The registering process can take some time, just be patient. Once done, you will be redirected to your app's page.

You will need to create a secret manually, as Azure AD does not create one for you automatically. Simply go to Certificates & Secrets and click on New client secret. Note that the secret will not be visible once you leave the page, so copy it then and not later!

| Name in Azure AD Application page        | Name in the config file |
| ---------------------------------------  | ----------------------- |
| Overview -> Application (client) ID      | `idpOAuthClientId`     |
| Certificates & Secrets -> Client secrets | `idpOAuthSecret`       |

You should also add redirection URIs based on where the front-end is served. The path is `/redirect/idProvider`, so, if your website will be served at `https://myawesomesite.com`, you must add the redirection URI `https://myawesomesite.com/redirect/idProvider`.

### Google

```yaml
idProvider:
  url: https://accounts.google.com
  name: Google
  icon: { file: google_icon.svg, contentType: "image/svg+xml" }
```

For `google_icon.svg`, you can use simpleicons.org's logo, which you can find [here](https://simpleicons.org/icons/google.svg). Place it next to your configuration file.
