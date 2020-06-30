<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div id="about">
        <div id="banner">
            <img id="logo" src="../../assets/logo.svg"/>
            <h1 class="title">EpiLink</h1>
        </div>

        <div id="button-container">
            <a id="sources" href="https://github.com/EpiLink/EpiLink" target="_blank">
                <img id="github" src="../../assets/github.png" />
                {{ $t('about.sources') }}
            </a>
        </div>

        <div id="disclaimer">
            {{ $t('about.disclaimer')[0] }}
            '<span class="instance">{{ instance }}</span>'
            {{ $t('about.disclaimer')[1] }}
        </div>

        <div id="authors-section">
            <h2 class="title" v-html="$t('about.authors')" />

            <div id="authors">
                <a class="author" href="https://github.com/utybo" target="_blank">
                    <img class="avatar" src="../../assets/utybo.png" />
                    <span class="name">Matthieu 'Zoroark' Stombellini</span>
                    <span class="role">Back-end</span>
                </a>
                <a class="author" href="https://github.com/Litarvan" target="_blank">
                    <img class="avatar" src="../../assets/litarvan.png" />
                    <span class="name">Adrien 'Litarvan' Navratil</span>
                    <span class="role">Front-end</span>
                </a>
            </div>
        </div>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    export default {
        name: 'link-about',
        computed: mapState({ instance: state => state.meta && state.meta.title })
    }
</script>

<style lang="scss" scoped>
    #about {
        display: flex;
        flex-direction: column;
        justify-content: center;
    }

    #banner {
        display: flex;
        justify-content: center;
        align-items: center;

        margin-top: 4px;

        #logo {
            width: 85px;
            margin-right: 20px;

            border-radius: 5px;
        }

        .title {
            font-size: 56px;

            margin: 0;
        }
    }

    #button-container {
        display: flex;
        justify-content: center;

        margin-top: 22px;
        margin-bottom: 12px;

        #sources {
            display: flex;
            justify-content: space-evenly;
            align-items: center;

            width: 185px;

            padding: 10px 10px;

            color: white;
            background-color: black;

            border-radius: 4px;

            #github {
                height: 25px;
            }
        }
    }

    #disclaimer {
        text-align: center;

        font-style: italic;
        padding: 0 15px;

        .instance {
            font-weight: bold;
        }
    }

    #authors-section {
        .title {
            text-align: center;

            margin-top: 10px;
            margin-bottom: 15px;
        }

        #authors {
            display: flex;
            justify-content: space-evenly;

            .author {
                display: flex;
                flex-direction: column;
                align-items: center;

                .avatar {
                    height: 100px;

                    border-radius: 50%;
                }

                .name {
                    font-size: 18px;

                    margin-top: 10px;
                }

                .role {
                    font-style: italic;
                    font-size: 15px;
                }
            }
        }
    }

    @media screen and (max-width: 535px) {
        #authors-section {
            .title {
                margin-bottom: 0;
            }

            #authors {
                flex-direction: column;

                padding: 0 20px;

                .author {
                    flex-direction: row;
                    justify-content: center;
                    margin-top: 7px;

                    .avatar {
                        height: 45px;
                    }

                    .name {
                        flex-grow: 1;

                        font-size: 16px;

                        margin-left: 10px;
                        margin-top: 0;
                    }

                    .role {
                        font-size: 14px;
                    }
                }
            }
        }
    }

    @media screen and (max-width: 425px) {
        #authors-section {
            .title {
                font-size: 21px;
            }

            #authors {
                padding: 0 11px;

                .author {
                    .avatar {
                        height: 30px;
                    }

                    .name {
                        font-size: 14px;
                    }

                    .role {
                        font-size: 11px;
                    }
                }
            }
        }
    }

    @media screen and (max-width: 365px) {
        #banner {
            #logo {
                width: 65px;
            }

            .title {
                font-size: 46px;
            }
        }

        #disclaimer {
            font-size: 14px;
        }

        #button-container #sources {
            width: 175px;
            padding: 9px 10px;

            font-size: 15px;

            #github {
                height: 22px;
            }
        }

        #authors-section #authors .author .name {
            font-size: 13px;
        }
    }
</style>