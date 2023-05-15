package nl.tudelft.trustchain.peerai

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.RequiresApi
import android.widget.*
import kotlinx.android.synthetic.main.fragment_peer_a_i.results
import kotlinx.android.synthetic.main.fragment_peer_a_i.searchview
import nl.tudelft.trustchain.common.ui.BaseFragment
import org.apache.commons.text.similarity.CosineSimilarity

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader

data class MyData(val items: List<String>)

class PeerAIFragment : BaseFragment(R.layout.fragment_peer_a_i) {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*

            val searchQuery = "Lorem ipsum";
            val features = searchQuery.words();
            val document = listOf("asdaskjasd asdasdjklasjdas ipsum", "hello world");
            val corpus = document.map{it.normalize().bag()};
            val bags = corpus.map{smile.nlp.vectorize(features,it)};
            val data = smile.nlp.tfidf(bags);

        */


    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {


        // on below line we are initializing adapter for our list view.
        val view: View = inflater.inflate(R.layout.fragment_peer_a_i, container, false)

        view.findViewById<ListView>(R.id.results).adapter = ArrayAdapter<String?>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                arrayOf("assda", "asdasd")
            )


        view.findViewById<SearchView>(R.id.searchview).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // on below line we are checking
                // if query exist or not.
                val items = listOf("apple", "banana", "cherry", "date", "elderberry")


                return false
            }

            @RequiresApi(Build.VERSION_CODES.N)
            override fun onQueryTextChange(newText: String): Boolean {
                // if query text is change in that case we
                // are filtering our adapter with
                // new text on below line.


                val list = findMostSimilarItems(newText,0.4);

                view.findViewById<ListView>(R.id.results).adapter = ArrayAdapter<String?>(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    list
                )

                (view.findViewById<ListView>(R.id.results).adapter as ArrayAdapter<String?>).notifyDataSetChanged();
                return false
            }
        });


        // on below line we are adding on query
        // listener for our search view.



        return view;
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun findMostSimilarItems(query: String, threshold: Double): List<String> {
        val cosine = CosineSimilarity()
        val items = arrayOf(
            "Partition 36 The Optic Nerve electronic trance",
            "Chrome Sparks Steffaloo electronic indie",
            "Die Romantik Narcissist's Waltz alternative indie rock",
            "XTRNGR Sunshine ambient electronic experimental fuzz idm",
            "Jazz Pad BE the Sun acoustic blues electric jazz poetry",
            "Philip James Nights ambient dream.pop electronic experimental psychedelic",
            "Cullah Firebird electronic folk soul",
            "adcBicycle Malignant Cove ambient dream.pop electronic experimental indie",
            "Tigerberry Cold Wave indie synthpop vaporwave witch.house",
            "Pk jazz Collective The Farewell downtempo indie.rock psychedelic synthpop",
            "[\u02c8\u0261lasn\u0259s\u02b2t\u02b2] Cellula Mesenchymatica Praecursoria 2010s avant.garde experimental harsh.noise noise",
            "Serious Mastering Ego electronic",
            "Serious Mastering La chaleur du soleil electronic",
            "Oxidant Deconstruct hardcore.punk powerviolence punk",
            "Tired Of Everything Behind The Blade hardcore.punk powerviolence punk",
            "sds la maison du soleil ambient electronic experimental",
            "YTCracker A Side Quest for Fractional Cents hip.hop nerdcore",
            "Axium Waves Origin electronic synthwave vaporwave",
            "SA ST 24 breakcore drum.n.bass jungle",
            "Breakcorist00 SA ST breakcore drum.n.bass electronic jungle",
            "SA ST Patrik ambient relaxing",
            "Panda Dub discography drum.n.bass",
            "[\u02c8\u0261lasn\u0259s\u02b2t\u02b2] The Old World 2010s avant.garde experimental harsh.noise noise",
            "QQROACH RIVER (Radio Edit) electronic idm techno",
            "QQROACH FORWARD electronic idm techno",
            "QQROACH LOVE THE DRUMMER electronic idm techno",
            "DJ Chewitt II Phae EP alternative electronic experimental funk house",
            "ch4rl33 OMS-2: Oceanic Muzak School dance electro electronic indie",
            "[\u02c8\u0261lasn\u0259s\u02b2t\u02b2] T-90 2010s avant.garde experimental harsh.noise noise",
            "Rchetype Reset drum.n.bass dubstep electronic",
            "YTCracker introducing neals chiptune cyberpunk electronic hip.hop nerdcore",
            "mickmon Ice electronic house pop",
            "Serious Mastering Game Music ambient",
            "Kimiko Ishizaka J.S. Bach: The Art of the Fugue (Kunst der Fuge), BWV 1080 classical instrumental piano",
            "Brokenkites Kaye Coby ambient electronic noise trance trip.hop",
            "Brokenkites Broken Hearts and Broken Bones ambient electronic experimental idm indie",
            "Dj FU-Q Original Mix 18 Hours of Grey Dope Funk electronic hip.hop house",
            "SA ST July electronic psychedelic trance",
            "SA ST August breakcore drum.n.bass electronic jungle",
            "Noised Noised noise",
            "\u5e7d\u970a, T O G E T H E R! Crypto Summer ambient vaporwave",
            "\u5e7d\u970a, T O G E T H E R! Night Cruisin' ambient vaporwave",
            "Flembaz Caruma electronic minimal psychedelic techno trance",
            "Flembaz Tripalle electronic psychedelic techno trance",
            "Cullah Trinity blues",
            "Cullah Cullah The Wild blues",
            "Rubl' Sdachi Ne Nado (Keep the change) garage.rock rock russian.rock",
            "CONDeNSE Blue Sky alternative.rock pop.rock rock",
            "Japanese Torture Comedy Hour Dolphin Meat experimental harsh.noise noise power.electronics",
            "Japanese Torture Comedy Hour The 24 hour Japanese Torture Comedy Marathon experimental harsh.noise noise power.electronics",
            "Cullah \u00bd electronic folk psychedelic soul",
            "Kizito My First Album acoustic",
            "Serious Mastering Mobius - Red ambient electronic",
            "Csum Tobetozero electronic experimental idm",
            "Cullah Cullahtivation electronic folk hip.hop psychedelic rock",
            "Cullah Spectacullah blues country dance electronic hip.hop",
            "Cullah Cullahsus electronic folk funk hip.hop rock",
            "crt_head Turbo Force Gunmetal Zero cyberpunk electronic synthwave",
            "Omri Lahav 0 A.D. Soundtracks acoustic ambient classical folk game",
            "Fog Lake inference 3 alternative indie lo.fi",
            "EVA Rear View 80s electronic synth",
            "Josh Woodward The Shade From Our Trees acoustic experimental folk pop rock",
            "Josh Woodward Addressed to the Stars acoustic folk pop rock",
            "John Stuart Home For Now acoustic alternative folk",
            "XsaladcrusherX A Big Fucking Let Down chiptune grindcore hardcore powerviolence",
            "XsaladcrusherX Pizza Pentaholocrust grindcore hardcore powerviolence",
            "XsaladcrusherX I Just Wanna Listen To Iron Maiden grindcore hardcore powerviolence",
            "XsaladcrusherX Turbo Speed! fastcore grindcore hardcore powerviolence",
            "3. Stock Links none 2016 alternative darkwave experimental german",
            "Rich Reuter The Captain dankjewel dayton dnkjewel rock",
            "Ryan Jensen The Here After acoustic experimental vocal",
            "Torp Els Segadors metal",
            "SubMine 2 Doom Metal EPs deathmetal doom metal",
            "Now Endeavor Ex & Woes dance hip.hop pop",
            "Perry Perry's Cabin Days hip.hoprap",
            "DAVIEL Expanding Consciousness EP ambient experimental psychedelic trance",
            "The A.J. Gatz Project Who is A.J. Gatz? Volume 2 acoustic alternative folk indie pop",
            "Cullah Killah Cullah electronic electronica hip.hop indie instrumental",
            "Cullah Adolessonce electronic",
            "Cullah Cullahmity blues folk",
            "Japanese Torture Comedy Hour Wheel Of Distortion experimental harsh.noise noise power.electronics",
            "Weekend Nachos Still (Sampler) hardcore powerviolence",
            "The A.J. Gatz Project Who is A.J. Gatz? Volume 1 acoustic alternative independant post.rock rock",
            "seedmole Hibernation ambient drone experimental improvizational psychedelic",
            "seedmole Converzhe ambient drone experimental jazz psychedelic",
            "seedmole This Should ambient drone experimental psychedelic space",
            "Porch Dogs From the Center of the Sun ambient drone experimental progressive soundscape",
            "The Jonny Monster Band Bad Times Before alternative blues rock",
            "Bitly Party all night dance electronic house",
            "Give Up The Goods 1221 metal",
            "Serious Mastering Synfonie electronic",
            "Serious Mastering Silent electronic",
            "Serious Mastering Mobius - Blue ambient electronic",
            "Serious Mastering Life electronic",
            "Serious Mastering Fire electronic",
            "Serious Mastering Bio electronic",
            "Serious Mastering ab11001101 electronic",
            "Lissi Dancefloor Disaster Lissi Dancefloor Disaster electronic electropop",
            "Skeetones Retrospektive breaks dubstep electronic",
            "Cassiopeia Cassiopeia ambient post.rock shoegaze space",
            "Panda Dub The Lost Ship dub electronic reggae",
            "The Knife Shaking the Habitual drone electronic experimental",
            "Chando First Words drum.n.bass electronic house synth",
            "Chando Chandeliers & Champagne drum.n.bass electronic hip.hop house synth",
            "Kontext Sakura hip.hop",
            "Phat Kid teller1 acid electronic experimental idm",
            "YTCracker earthbound - adventures of the sound stone vol. 1 chiptune electronic hip.hop nerdcore",
            "dios trio II alternative indie math.rock",
            "Flembaz Wild Horse house melodic progressive techno trance",
            "Band of Beards Fuerteventura punk",
            "XsaladcrusherX MILxDREAD grindcore hardcore powerviolence",
            "Bitly Scream electronic house techno trance",
            "Rchetype Recharge drum.n.bass electronic",
            "Afternoon Coffee Singles Collection 2011-2012 afternoon.coffee indonesia pop world.music yogyakarta",
            "Vitodito & Oza Kawaii electro.house progressive.house",
            "Dominic Matar Where We Begin alternative indie",
            "Judd Madden Doomgroove alternative instrumental metal post.metal stoner",
            "Big Mean Sound Machine Marauders afrobeat dance experimental funk psychedelic",
            "Skull Of My Ram SVOBODA 2010s beats electronic experimental hip.hop",
            "Skull Of My Ram ZVEZDOPAD 2010s 80s electronic new.retro retro",
            "The Next Hundred Years Troppo metal rock stoner",
            "Red Sled Choir Wintersongs folk",
            "Fire Spoken by the Buffalo Air Your Grievance instrumental post.metal post.rock",
            "JKD campus acoustic folk indie",
            "Seatraffic Crimes ambient dream.pop indie psychedelic",
            "Aquacat The Stench Of Death black.metal grindcore hardcore noise powerviolence",
            "Funeral Hammer XsaladcrusherX black.metal doom.metal grindcore hardcore noise",
            "bugXpunch XsaladcrusherX grindcore hardcore powerviolence",
            "XsaladcrusherX All The Fixins' grindcore hardcore powerviolence",
            "XsaladcrusherX Fuck Your Shit grindcore hardcore powerviolence",
            "XsaladcrusherX Land Before Grind grindcore hardcore powerviolence",
            "XsaladcrusherX Scumstache grindcore hardcore powerviolence",
            "XsaladcrusherX Fuck. grindcore hardcore powerviolence",
            "XsaladcrusherX XsaladcrusherX Plays A Song By bugXpunch grindcore hardcore powerviolence",
            "XsaladcrusherX Live And Learn grindcore hardcore powerviolence",
            "Slim The Pineapple Kevakin 7 dance electronic happy.hardcore metal techno",
            "JimeCide SBX Invasion Soundtrack chiptune electronic",
            "Luke Seymoup Poke'Gods emo emo.punk folk.punk indie.punk indie.rock",
            "Luke Seymoup The Professional alternative.punk alternative.rock australian diy diy.punk",
            "Luke Seymoup Songs I Wrote In High School, Vol. 1 australian diy diy.punk emo emo.punk",
            "Luke Seymoup Hand-Me-Downs alternative.punk alternative.rock australian folk.punk indie.punk",
            "Luke Seymoup Burnett Street australia folk.punk melbourne pop.punk punk",
            "Luke Seymoup Uke Seymoup acoustic acoustic.punk alternative.punk alternative.rock australian",
            "Luke Seymoup MulderScully alternative.punk alternative.rock australian folk.punk indie.punk",
            "Panda Dub Horizons dub electronic reggae",
            "KarmasynK Kristina Aqua 2010s electronic psychedelic",
            "Helicalin Everyting So Stoned, Everything Is Bad 2010s alternative downtempo jazz trip.hop",
            "Cambrian Explosion The Sun 2010s experimental psychedelic",
            "The Way We Were in 1989 The Way We Were in 1989 2010s folk",
            "Glenntai Silly Hats Only 2010s chiptunes electronic",
            "bertycox Synesthetism 2000s electronic",
            "bertycox The Signal 2010s electronic",
            "CWF bertycox 2010s electronic",
            "Midoriiro Voice Infect 2000s electronic",
            "bertycox Brain Washing 2000s electronic",
            "Comfort Avalon rock",
            "Emanuel and the Fear The Janus Mirror classical indie orchestral rock",
            "Emanuel and the Fear Emanuel and the Fear rock",
            "Emanuel and the Fear Listen classical electronic experimental indie orchestral",
            "DEERPEOPLE DEERPEOPLE indie",
            "Boatrocker Turn It the Fuck Up! indie punk rock",
            "Monolith Eclipse metal",
            "Ektoise Ektoise alternative ambient electronic shoegaze",
            "Secheron Peak Slow Gravity ambient electronic glitch idm post.rock",
            "CHEjU Rorschach ambient idm",
            "Uookasz A Few of Autumn ambient world.music",
            "Terraformer The Sea Shaper experimental instrumental math.rock post.metal post.rock",
            "Violent Sun Self-Titled experimental jazz psychedelic rock",
            "Conveyor Mukraker experimental indie rock",
            "Pitchman Smoke and Mirrors hardcore.punk punk",
            "A Fight You Can't Win Shout First / Last Words alternative grunge punk",
            "Nostalgic Story The Last Stretch In This Direction ambient chiptune electronic experimental",
            "Nostalgic Story Sorry And Goodnight ambient electronic experimental",
            "Quiet Americans Medicine noise pop rock",
            "Lessazo Soleil d'hiver african world.music",
            "Young Heel LYD electronic indie pop",
            "Monocure In Love With Myself punk rock",
            "Decks Yr Sucha Deck indie psychedelic rock",
            "Paul Stewart Parkade Songs acoustic folk",
            "Paul Stewart Permanence acoustic folk",
            "UV The UV EP electronic experimental",
            "SNOWMINE LAMINATE PET ANIMAL indie jungle orchestral pop psychedelic",
            "The Black Atlantic Darkling, I Listen acoustic folk pop",
            "Wise Children The Woods folk indie pop",
            "Racing Glaciers Racing Glaciers alternative folk indie post.rock rock",
            "Eolune Canvas electronic experimental indie rock",
            "Corpus Callosum Corpus Callosum folk folk.pop",
            "Holmes I Will Never Be Free alt.country folk indie pop",
            "fanisatt View easy.listening instrumental pop",
            "Jesus Seashell Ghoul / 1989 EP ambient dream.pop idm indie psychedelic",
            "All shall be well (and all shall be well and all manner of things shall be well) ROODBLAUW instrumental post.rock",
            "Holmes Have I Told You Lately That I Loathe You alt.country folk indie pop",
            "Peter Larson & Friends Deez funk jazz",
            "Sadsic [birthday] electronic experimental",
            "Sadsic [sun] ambient electronic experimental",
            "Sadsic [sic] electronic experimental",
            "Sadsic [spring of eternity] ambient big.beat electronic experimental",
            "Suhov Heartbox trip.hop",
            "Chris Zabriskie Vendaface ambient electronic",
            "z0rg Of Lyres & Chocolate instrumental orchestral",
            "Beats Antique Brass Menazeri dubstep electronic experimental orchestral world.music",
            "Fire Spoken by the Buffalo Hiatus instrumental post.metal post.rock rock",
            "My bubba & Mi Wild & You blues folk pop",
            "Conveyor Sun Ray electronic experimental indie pop rock",
            "Oven Rake Dead Bear Mountain chiptune electronic",
            "Ephixa Zelda Step dubstep house video.game",
            "Aavepy\u00f6r\u00e4 Aarnimaa psychedelic techno trance",
            "Glander Variations ambient soul",
            "Plastic Penguin Bande Dessin\u00e9e ambient electronic",
            "Fit and the Conniptions Sweet Sister Starlight blues folk rock",
            "Isrra Proton dubstep electronic experimental",
            "Resykle Dust & Gravity drum.n.bass dubstep",
            "Phoenix McQueen Exile alternative",
            "Gwyn Fowler Patterns alt.country folk indie singer.songwriter",
            "Addae Dans Clown In The Woman Toilet minimal",
            "Chris Zabriskie Stunt Island ambient electronic",
            "Nine Inch Nails Ghosts I-IV ambient electronic experimental industrial instrumental",
            "The Stereophones Trouble indie pop rock",
            "The Curiously Strong Peppermints Echoes From The Ultraviolet Fuzz indie pop psychedelic rock",
            "Phoenix McQueen Exile The Instrumentals alternative instrumental",
            "Ektoise Down River alternative ambient electronic experimental shoegaze",
            "Sadsic Symbol ambient electronic experimental",
            "Edamame Drew Major chillout electronic hip.hop idm instrumental",
            "Commonplaces A private sea alternative post.rock",
            "Maharajah Ninushkasayan experimental",
            "Broken Fences Broken Fences acoustic indie",
            "Stories From The Lost For Clouds alternative post.metal post.rock",
            "Maharajah Charl experimental hip.hop",
            "Kikyo Maaka (\u771f\u7d05\u304d\u304d\u3087) SeeU electronic jpop",
            "Big Mean Sound Machine Warrior afrobeat dance funk jazz psychedelic",
            "Panda Dub Shapes and Shadows dub electronic reggae",
            "DIVEWITHIN \u017dnivie\u0144 9-11 ambient drone",
            "Sunday Comes Afterwards I'll Stay Home for Christmas christmas comedy geekfolk",
            "YTCracker Strictly for My Streamers chiptune cyberpunk electronic hip.hop nerdcore",
            "bertycox Broken Piano 2000s electronic",
            "bertycox Feat 2000s electronic",
            "bertycox Film'O Graf 2000s electronic",
            "bertycox Free Climb 2000s electronic",
            "bertycox Last Spring 2000s electronic",
            "bertycox Light of Abysses 2000s electronic",
            "bertycox Remember 2010s electronic",
            "bertycox New Day 2000s electronic",
            "bertycox Robot in Love 2000s electronic",
            "Freq36 LiinK 2010s dark.psy hi.tech psychedelic",
            "Strobcore Funky Music 2000s hardcore",
            "The Curiously Strong Peppermints Endless Fields of Poppy psychedelic",
            "Nine Miles High Keep It Loud EP alternative rock",
            "Gary Clinton Film Score alternative",
            "Marcel Phosph The Ledge ambient chillstep dubstep electronic electronica",
            "SORRY OK YES 2012 Singles alternative indie rock",
            "SORRY OK YES Rubberized alternative indie rock",
            "Goliath And The Giants Done Deed indie pop rock surf",
            "Spassm Ant Mas emo hardcore indie progressive.metal punk",
            "Tabasco Fiasco One EP alternative ambient experimental",
            "Le Vol Last Day of February alternative indie",
            "Decade In Exile Take Me Back To Ordinary dream.pop experimental.pop shoegaze",
            "Decade In Exile Decade In Exile dream.pop experimental.pop psychedelic shoegaze",
            "Decade In Exile After The Counter dream.pop experimental.pop shoegaze",
            "Decade In Exile Steel Pin Raindrops dream.pop experimental.pop shoegaze",
            "Bear Baiting Bear Baiting ambient instrumental noise post.rock rock",
            "Arms Of Ra SON\u200b.R26 / Unnamed metal sludge",
            "UV Tourist I Thought You Were Drone electronic indie pop punk",
            "\u2591\u2592\u2593 \u2588 \u2584 \u2588 \u2588 \u2584 \u2588\u2588 \u2584 \u2588\u2588 \u2584\u2588 ambient drone drum.n.bass electronic psychedelic",
            "Queen Orlenes Helicopters indie pop rock",
            "Ink Domino Tones alternative electronica experimental hip.hop jazz",
            "DEERPEOPLE EXPLORGASM folk indie psychedelic rock",
            "J. Dujardin Ghosts of the Grey post.rock",
            "Inca Gold Inca Gold III alternative indie pop psychedelic shoegaze",
            "Yohuna Revery ambient dream.pop keyboard pop reverb",
            "sssandcas\u2020lesss haunted glitter and the hazy lake chiptunes electronic indie",
            "Adey Rogue alternative classical ethereal singer.songwriter",
            "Informant Signal chillwave dance electronic grime progressive.house",
            "Midi Matilda Red Light District electronic indie pop",
            "Kalle Kaasinen ILME alternative indie pop",
            "Decks Helix Street psychedelic rock",
            "Caution Wet Ceiling View From the Top ambient dreamy idm trip.hop",
            "Ruffin Poe Hobo In A Pilot Cabin minimal",
            "equalszee =Z rock",
            "Technikiller The Smallest Positive Integer Not Definable in Under Eleven Words experimental instrumental math.rock",
            "SEVEN Shine downtempo",
            "Chalices of the Past 2 RUDE 8.bit dancehall electronic lo.fi pop",
            "Shroud Eater 3-song EP metal",
            "Six Star General Already on One rock",
            "Handgrenades Demo to London b/w Coma Dos 45 post.punk punk",
            "The Sexy Accident You're Not Alone indie pop",
            "Azoora Graciellita experimental trip.hop",
            "Flyafter Today I'm With You indie",
            "LukHash Digital Memories 8bit electronic nu.metal rock",
            "The Paparazzi Rococo experimental.pop power.pop psychedelic.rock",
            "Univeria Zekt Unnamables progressive",
            "Chrome Sparks Steffaloo electronic indie pop",
            "Labbed Labhits 2011 chipmusic chiptune electronic",
            "Gwyn Fowler Lose Your Blue acoustic folk indie singer.songwriter",
            "ch4rl33 Orbital Muzak School Year 1 (OMS-1) electronic progressive",
            "Cullah Pack -A- Clones alternative hip.hop instrumental milwaukee underground",
            "Sadsic [souls of winter] ambient big.beat electronic experimental",
            "MC Skatty MC Whizzkid hip.hop",
            "DJ Davies MC Skatty hardcore",
            "DJ Davies MC Skatty hardcore",
            "DJ Davies MC Skatty hardcore",
            "Republicans Suck Tiger Mountain Peasant Song chiptune electronic",
            "Republicans Suck Happy. electronic",
            "Sylvain the Librarian Staff Only No Vikings indie",
            "Addae Dans Narcotic Lion dubstep",
            "Tleilaux indisguise house idm",
            "Keno Purtati de vant hip.hop",
            "Mr Selfdestruct Kala Pesa electronic house industrial minimal progressive",
            "pablo denegri Versteck experimental minimal techno",
            "Greendjohn Loophole ambient film instrumental orchestral soundtrack",
            "Oleg Serkov Epoch Symbol experimental instrumental meditative metal rock",
            "someone else bring it down house minimal techno",
            "Asura (Red Puma) 360 downtempo electronik new.age tribal vocal",
            "Dom The Bear Creating Worlds ambient chillout electronic instrumental orchestral",
            "zero-project celtic dream ambient celtic instrumental meditative piano",
            "fidget bread & circus experimental minimal",
            "Elektroniska Syrsor The well of Sendell electronic experimental indie",
            "Zo\u00eb Blade Hello Calm ambient electronic techno",
            "Wet Wings Skin to Soil folk indie pop psychedelic",
            "Creepers Songs From the Green House indie pop rock",
            "And the Giraffe Something for Someone folk indie post.rock",
            "Sadsic [b. now] hip.hop instrumental",
            "The Echelon Effect Seasons Part 2 ambient post.rock rock",
            "Awake in Sleep Awake in Sleep metal post.metal post.rock sludge",
            "Boreals Rome alternative ambient electronic experimental indie",
            "Aidan Knight Versicolour alternative pop pop.folk post.rock",
            "Nevermind the Name A Gaze into the Abyss ambient post.rock",
            "ch4rl33 NY, i xxxxd you dance electronic",
            "Matt Stevens Relic acoustic instrumental post.rock progressive.rock",
            "Austin Basham Little Foxes folk indie",
            "J. Dujardin This Will Be The Last ambient drone post.rock",
            "Torley Glitch Piano electronic idm instrumental melodic new.age",
            "Great American Desert Live on 90.3 KRNU alternative country folk indie",
            "Great American Desert The Monsters/Bathroom Sessions alternative country folk indie",
            "Great American Desert Homes alternative country folk indie",
            "The Distnce Dry Land alternative dream.pop electronic",
            "Flesh Forest The Elephant Only Zoo big.beat electronic experimental indie psychedelic",
            "cssc After Tides ambient electronic post.rock",
            "Kulor Alter-Ego chiptune",
            "Ices Ov Memoria ambient drone",
            "Buk Lau The Money hip.hop parody",
            "Buk Lau Vedy Racist hip.hop parody west.coast",
            "Erik Sumo Band The Ice Tower In Dub alternative disco dub electronic industrial",
            "Hungry Lucy Pulse of the Earth trip.hop",
            "Ninetails Rawdon Fever alternative electronic math.rock post.rock progressive",
            "Duarte Ferreira A House in Iceland 2010s folk",
            "Nic Falcon Playing Fair 2010s indie",
            "Derek Clegg Across Town 2010s folk indie",
            "Kamas Vapor Blue Sensualize ambient electronic idm trip.hop",
            "Kamas Free ambient downtempo electronic idm trip.hop",
            "Spanish Prisoners Downtown Chicagoland dream.pop electronic psychedelic rock",
            "Conelrad Five Automatic Landings ambient downtempo electronic indie",
            "The Flashbulb Opus at the End of Everything ambient electronic idm jazz post.rock",
            "Saint Bernard The Spirit of the Stairs alternative folk pop singer.songwriter",
            "Blind Scientist The Science Of Drifting Apart experimental hip.hop",
            "Efflorescent Everything Was ambient electronic experimental",
            "Walter Sickert & The Army of Broken Toys ANTICLAUS SUPERSTAR folk rock",
            "Torley eightbit.me 8.bit chiptune electronic video.game",
            "Torley Dream Journal 2 ambient electronic melodic trance",
            "EUS Tras El Horizonte ambient experimental post.rock",
            "Swamp Three Cheers for the Firing Squad doom metal sludge stoner",
            "Tortilla Pass The Secret Deity bass beats electronic experimental fuzz",
            "Xihilisk Xihilisk downtempo dubstep electronic experimental idm",
            "Big Mean Sound Machine Ouroboros afrobeat dance funk jazz psychedelic",
            "Subsist Purist drum.n.bass electronic experimental",
            "Customer Soft Comedy ambient downtempo electro electronic folktronica",
            "ARCHNGL Liisa Lagoun pop urban.pop",
            "Tall Tales All we can do is sing indie pop",
            "A Fight You Can't Win A Fight You Can't Win alternative grunge punk rock",
            "NP Sex_(VI) electronic experimental glitch idm industrial",
            "Holmes Wolves alt.country folk indie pop",
            "Holmes Anna Gottfries alt.country folk indie pop",
            "Holmes So Far, So So alt.country folk indie pop",
            "Polinski Big Black Delta electronic video.game",
            "MC Skatty MC Whizzkid experimental",
            "DJ Davies MC Skatty hardcore",
            "mote. dust particle ambient chill downtempo electronic",
            "Obstacles Oscillate experimental instrumental math.rock psychedelic space.rock",
            "Addae Dans Black And White Sunrise II dubstep",
            "...And Stars Collide ...And Stars Collide instrumental post.rock",
            "...And Stars Collide When Our Eyes First Met instrumental post.rock",
            "Bekeschus The Marx Trukker dub.techno electronic",
            "Norfolk The 71 Functions of Consciousness indie",
            "1dB Exploratory Button Pushing electronic experimental",
            "Marvu Underground Jazz electronic minimal techno",
            "Mr Selfdestruct Gamma Overload dedicated.to.the.strong.people.of.japan dub experimental industrial panda.cd.exclusive",
            "Dj Vax1 Can't Sleep deephouse",
            "Juho the Panda Brothers experimental instrumental post.rock shoegaze",
            "zero-project e-world ambient chillout electronic experimental instrumental",
            "Datacrashrobot Asynchronous I/O electronic experimental",
            "Tryad Joana Smith ambiance ambient electronic industrial loungemix",
            "Tryad Listen ambient atmospheric classical electronic ethereal",
            "pornophonique 8-bit lagerfeuer 8bit acoustic electronic gameboy guitar",
            "Christian McKee Get Confident, Stupid electronic indie pop",
            "Perfect Me The Place That I Call Home electronic",
            "Ethan Kennedy Raucous blues",
            "We Swim You Jump We Swim You Jump indie pop",
            "Wauterboi Conversations With Myself electronic",
            "Munn til Munn Metoden EP Volume I electronic",
            "Glorie Glorie experimental instrumental post.rock progressive.rock shoegaze",
            "You Me and The People From the Attic acoustic electronic folk pop post.rock",
            "Addae Dans Myndust downtempo minimalism psychedelic",
            "DJ Davies MC Skatty hardcore",
            "Turn off your television Turn off your television folk indie pop rock",
            "Our Ceasing Voice Live ambient experimental post.rock shoegaze",
            "The Bell Beat The Carrot Chase alt.country folk indie",
            "The Bell Beat Our Manderley alt.country folk indie",
            "Middle American Princess Sophomore Debut indie pop",
            "Clouds as Oceans TIDES experimental indie instrumental post.rock shoegaze",
            "Citizens Of The Empire Citizens Of The Empire experimental instrumental post.rock progressive",
            "Man & Ghost Shoutalong folk rock",
            "Man & Ghost Noir Hill folk indie pop rock",
            "Gavin Coetzee Orange Forest acoustic blues folk reggae rock",
            "Informant Glowing Up drum.n.bass dubstep electronic house",
            "Seatraffic Seatraffic dream.pop electronic indie pop psychedelic",
            "Chip's Challenge Halfbit Hero 8.bit chiptune electronic rock",
            "Disassembling Rainbows Winchester alternative classical electronic indie rock",
            "KIDS. Sledding With Tigers folk punk",
            "Ninetails Ghost Ride the Whip alternative math.rock rock",
            "Andrew Judah Albino Black Bear folk indie pop",
            "Crystal Boys Skeletons indie lo.fi rock",
            "Wet Stallions Wet Stallions I hip.hop psychedelic soul",
            "Wet Stallions Wet Stallions II hip.hop psychedelic",
            "Town Portal Vacuum Horror math.rock post.rock",
            "Matt Stevens Ghost acoustic experimental post.rock progressive.rock",
            "Matt Stevens Silent Night acoustic christmas instrumental post.rock progressive.rock",
            "Sadsic [b. forever] electronic",
            "Almeeva EP#1 ambient electronic minimal",
            "Time and Place Scarlet acoustic folk punk",
            "Yellow Ostrich The Morgan Freeman EP rock",
            "Somniaferum Lost alternative instrumental post.rock",
            "The Echelon Effect Seasons Part 1 ambient instrumental post.rock rock",
            "Casey LaLonde Thank You ambient electronic idm synth.pop trip.hop",
            "Casey LaLonde In June (Stems) electronic idm synth.pop trip.hop",
            "Casey LaLonde In June electronic idm synth.pop trip.hop",
            "Casey LaLonde Hope Against Hope electronic idm synth.pop trip.hop",
            "Casey LaLonde Beware! electronic idm synth.pop trip.hop",
            "Prektel descent ambient minimal techno",
            "Moodix Relax Capsule Vol. 1 ambient chill downtempo",
            "Proviant Audio Mushrooms futurejazz jazz nujazz",
            "Mon Petit Chou Chou Headlights dream.pop indie post.rock shoegaze",
            "Zeitgeist Darwin electronic experimental minimal",
            "Danny Basic Blast From the Past electronic house techno",
            "Athletes Fall Apart dream.pop indie lo.fi",
            "Dubbemo No Man's Land dub dub.techno electronic",
            "Burdeos The Other Space Stories dubstep experimental idm",
            "J. Dujardin Lessons in Failure post.rock",
            "Addae Dans Lumen minimalism",
            "Fly Boys Swish! hip.hop",
            "Howth Belly of the Beast folk indie",
            "The Willow & The Builder The Willow & The Builder folk indie",
            "Suhov Symphaty Modul hip.hop mambo sweetbeat",
            "Tom Caruana Welcome Aboard break dub dubstep funk hip.hop",
            "1492 The Lie The Three Kings ambient electronic progressive",
            "Asylum Voyage Amnesty electronic orchestral rock",
            "Zhe Nhir Somnolence ambient",
            "Zhe Nhir Songs For K ambient electronic",
            "Zhe Nhir Bit Children australian electronic",
            "Zhe Nhir Zhe End is Nhir electronic",
            "Sadsic [static atlas] electronic",
            "Sadsic [bedroom emotions] electronic",
            "Sadsic [sidewalk spark] electronic",
            "Velislav Ivanov \u041f\u043e\u0433\u043b\u0435\u0434\u044a\u0442 \u043d\u0430 \u0441\u044a\u0437\u0435\u0440\u0446\u0430\u0442\u0435\u043b\u044f (The Eye of the Beholder) alternative bulgarian",
            "Orison Orison rock",
            "equalszee This is Peculiar rock",
            "dialect dialect alternative",
            "dialect hoover alternative",
            "Society's Plague The Mercy Untold metal",
            "The Amorist The Amorist rock",
            "Drunken Barn Dance Grey Buried indie",
            "Toads, Inc. The Biggest Conch Shell I've Ever Seen electronic",
            "Essue Dove Sadsic experimental",
            "Lechuguillas Long Live The Chupacabra noise rock",
            "Guppies Live on Demolisten KXLU live rock",
            "Nobody Wave Headgeared indie",
            "Palloc A Mad But Perfect Plan indie",
            "Guppies In Reference to Something Forgotten rock",
            "Seb Butter rock",
            "Nick Perko Brush Your Teeth With Me ambient",
            "Awkward Paws Supposed to Smile indie",
            "Nomia Nomia instrumental post.rock rock",
            "Ptarmigan Our Ancient Friends folk indie",
            "weakness WEAKNESS rock",
            "Boatrocker Delicious Jams indie metal post.hardcore",
            "Josh Mease Wilderness indie",
            "Zahir Green Means Go noise rock",
            "fnessnej stay fresh, ey indie",
            "Philip Dickau This City, and You ambient",
            "Hail Infinity The Gyroscope post.rock rock shoegaze",
            "Adore Distorted Minds electronic",
            "Adore Beneath Us electronic",
            "Adore Emotion electronic",
            "Kowloon Walled City Gambling on the Richter Scale metal",
            "Squanto Go Go Gadget Grass Stains folk",
            "Sledding With Tigers WOAHbots! (And People) acoustic comedy",
            "Owen Gilbride Plate Tectonics ambient electronic experimental industrial",
            "Aperion Act of Hybris epic rock symphonic",
            "Velislav Ivanov \u0422\u044a\u0439 \u0431\u043b\u0438\u0437\u043a\u043e \u0434\u043e \u0441\u0430\u043c\u0438\u044f \u043d\u0435\u0431\u043e\u0441\u0432\u043e\u0434 (So close to the sky itself) bulgarian progressive.rock",
            "After Three Seconds Genius Loci alternative",
            "Giraffe Incognito The Pursuit Continues electronic indie",
            "Homestretch Comatose hardcore",
            "Is World Turning rock",
            "Nothing Poshlost indie shoegaze"
        );
        // Create term frequency vector for query
        val queryVec = createTermFrequencyVector(query)

        // Create list of items with their cosine similarity scores
        val scores = items.map { item ->
            val itemVec = createTermFrequencyVector(item)
            val score = cosine.cosineSimilarity(queryVec, itemVec)
            Pair(item, score)
        }

        // Sort the list of items by their cosine similarity scores in descending order
        val sortedScores = scores.sortedByDescending { it.second }

        // Extract the list of items from the sorted list of item scores that meet the threshold
        val sortedItems = sortedScores.filter { it.second >= threshold }.map { it.first }

        return sortedItems
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun createTermFrequencyVector(str: String): MutableMap<CharSequence, Int> {
        val vector = mutableMapOf<CharSequence, Int>()

        // Split the string into words and count their occurrences
        for (word in str.toLowerCase().split(" ")) {
            vector[word] = vector.getOrDefault(word, 0) + 1
        }

        return vector
    }

}
