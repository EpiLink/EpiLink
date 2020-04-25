<template>
    <div id="about">
        <div id="banner">
            <img id="logo" src="../../assets/logo.svg"/>
            <h1 class="title">EpiLink</h1>
        </div>

        <div id="button-container">
            <a id="sources" href="https://github.com/Litarvan/EpiLink" target="_blank">
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
</style>