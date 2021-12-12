// export CLASSPATH=`ls -1 *.jar | tr '\n' ':'`

import java.text.SimpleDateFormat;
import java.util.Date;
import groovy.util.slurpersupport.NodeChildren;

import org.bridgedb.IDMapperException;
import org.bridgedb.DataSource;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.rdb.construct.DBConnector;
import org.bridgedb.rdb.construct.DataDerby;
import org.bridgedb.rdb.construct.GdbConstruct;
import org.bridgedb.rdb.construct.GdbConstructImpl3;

commitInterval = 500
genesDone = new java.util.HashSet();
linksDone = new java.util.HashSet();

DataSourceTxt.init()

unitReport = new File("creation.xml")
// unitReport << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
unitReport << "<testsuite tests=\"12\">\n"

GdbConstruct database = GdbConstructImpl3.createInstance(
  "hmdb_chebi_wikidata_metabolites", new DataDerby(), DBConnector.PROP_RECREATE
);
database.createGdbTables();
database.preInsert();

blacklist = new HashSet<String>();
blacklist.add("C00350")
blacklist.add("C00157")
blacklist.add("C00422")
blacklist.add("C00165")
blacklist.add("C02530")
blacklist.add("C00416")
blacklist.add("C02737")
blacklist.add("363-24-6")
blacklist.add("104404-17-3")
blacklist.add("CHEBI:17636")
blacklist.add("HMDB0000912") // see bug #6
blacklist.add("HMDB00912") // see bug #6
blacklist.add("HMDB0006316") //wrong mappings to ChEBI, Pubchem Compound = 0
blacklist.add("HMDB06316") //wrong mappings to ChEBI, Pubchem Compound = 0

// Some compounds have more than one Wikidata items, e.g. because there are two Wikipedia pages
// Therefore, we blacklist one of them, to ensure we do not get two Wikidata QIDs
genesDone.add("Q26981430") // the other QID is Q190875, which should show up anyway

//inchiDS = DataSource.getExistingBySystemCode("Cin")
inchikeyDS = DataSource.getExistingBySystemCode("Ik")
chemspiderDS = DataSource.getExistingBySystemCode("Cs")
casDS = DataSource.getExistingBySystemCode("Ca")
pubchemDS = DataSource.getExistingBySystemCode("Cpc")
chebiDS = DataSource.getExistingBySystemCode("Ce")
keggDS = DataSource.getExistingBySystemCode("Ck")
keggDrugDS = DataSource.getExistingBySystemCode("Kd")
wikidataDS = DataSource.getExistingBySystemCode("Wd")
lmDS = DataSource.getExistingBySystemCode("Lm")
knapsackDS = DataSource.getExistingBySystemCode("Cks")
dtxDS = DataSource.getExistingBySystemCode("Ect")
drugbankDS = DataSource.getExistingBySystemCode("Dr")
iupharDS = DataSource.getExistingBySystemCode("Gpl")
chemblDS = DataSource.getExistingBySystemCode("Cl")
swisslipidsDS = DataSource.getExistingBySystemCode("Sl")
hmdbDS = DataSource.getExistingBySystemCode("Ch")

//vmhmetaboliteDS = DataSource.register ("VmhM", "VMH metabolite").asDataSource() //Add this to BridgeDb, update libraries, add to website!

chebiVersionFile = new File('data/chebi.version')
chebiVersion = "206"
if (chebiVersionFile.exists() && chebiVersionFile.canRead()) {
  chebiVersion = chebiVersionFile.text.trim()
}

String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
database.setInfo("BUILDDATE", dateStr);
database.setInfo("DATASOURCENAME", "HMDB-CHEBI-WIKIDATA");
database.setInfo("DATASOURCEVERSION", "HMDB5.0.20211102-CHEBI" + chebiVersion + "-WIKIDATA" + dateStr);
database.setInfo("DATATYPE", "Metabolite");
database.setInfo("SERIES", "standard_metabolite");

def addXRef(GdbConstruct database, Xref ref, String node, DataSource source, Set genesDone, Set linkesDone, boolean isPrimary) {
   id = node.trim()
   if (id.length() > 0) {
     // println "id($source): $id"
     ref2 = new Xref(id, source, isPrimary);
     if (!genesDone.contains(ref2.toString())) {
       if (database.addGene(ref2) != 0) {
          println "Error (addXRef.addGene): " + database.recentException().getMessage()
          println "                 id($source): $id"
       }
       genesDone.add(ref2.toString())
     }
     if (!linksDone.contains(ref.toString()+ref2.toString())) {
       if (database.addLink(ref, ref2) != 0) {
         println "Error (addXRef.addLink): " + database.recentException().getMessage()
         println "                 id(origin):  " + ref.toString()
         println "                 id($source): $id"
       }
       linksDone.add(ref.toString()+ref2.toString())
     }
   }
}

def addXRef(GdbConstruct database, Xref ref, String node, DataSource source, Set genesDone, Set linkesDone) {
  addXRef(database, ref, node, source, genesDone, linkesDone, (boolean)true)
}

def addAttribute(GdbConstruct database, Xref ref, String key, String value) {
   id = value.trim()
   // println "attrib($key): $id"
   if (id.length() > 255) {
     println "Warn: attribute does not fit the Derby SQL schema: $id"
   } else if (id.length() > 0) {
     if (database.addAttribute(ref, key, value) != 0) {
       println "Error (addAttrib): " + database.recentException().getMessage()
     }
   }
}

def cleanKey(String inchikey) {
   String cleanKey = inchikey.trim()
   if (cleanKey.startsWith("InChIKey=")) cleanKey = cleanKey.substring(9)
   cleanKey = cleanKey.replace("\"", "")
   cleanKey
}

// read the secondary ChEBI identifiers
deprChEBIFile = new File("deprecated_ChEBI.csv")
deprChEBIIDs = new java.util.HashSet();
if (deprChEBIFile.exists()) {
  deprChEBIFile.eachLine { line,number ->
    fields = line.split(",")
    secid = fields[0]
    deprChEBIIDs.add(secid)
  }
}

// load the HMDB content
counter = 0
hmdbFile = new File('hmdb_metabolites_split.zip')
if (hmdbFile.exists()) {
  def zipFile = new java.util.zip.ZipFile(hmdbFile)
  zipFile.entries().each { entry ->
     if (!entry.isDirectory() && entry.name != "hmdb_metabolites.xml") {
       inputStream = zipFile.getInputStream(entry)
       def rootNode = null
       try {
         rootNode = new XmlSlurper().parse(inputStream)
       } catch (Exception exception) {
         println "Error while reading XML from file $entry: " + exception.message
         return
       }
       error = 0
  
       String rootid = rootNode.accession.toString()
       String newid = null
       // println "HMDB original: ${rootid}"
       if (rootid.length() == 11) {
         if (rootid.startsWith("HMDB00")) {
           newid = rootid
           rootid = "HMDB" + rootid.substring(6) // use the pre-16 August 2017 identifier pattern
         } // otherwise: assume there only is a new ID
       } else if (rootid.length() > 4) {
         newid = "HMDB00" + rootid.substring(4)
       } else {
         error = 1
         println "Error (incorrect HMDB): " + rootid
       }
       // println "HMDB old: ${rootid} -> ${newid}"
       Xref ref = new Xref(rootid, hmdbDS, false);
       Xref newref = (newid == null) ? null : new Xref(newid, hmdbDS);
       if (!genesDone.contains(ref.toString())) {
         addError = database.addGene(ref);
         if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
         error += addError
         linkError = database.addLink(ref,ref);
         if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
         error += linkError
         if (newref != null) {
           addError = database.addGene(newref);
           if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
           error += addError
           linkError = database.addLink(newref,newref);
           if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
           error += linkError
           linkError = database.addLink(ref,newref);
           if (linkError != 0) println "Error (addLinkNewOld): " + database.recentException().getMessage()
           error += linkError
         }
         genesDone.add(ref.toString())
       }
  
       // add the synonyms
       addAttribute(database, ref, "Symbol", rootNode.name.toString());
       rootNode.synonyms.synonym.each { synonym ->
         addAttribute(database, ref, "Synonym", synonym.toString())
       }
       addAttribute(database, ref, "Synonym", rootNode.traditional_iupac.toString());
       addAttribute(database, ref, "Synonym", rootNode.iupac_name.toString());
  
       // add the SMILES, InChIKey, etc
       addAttribute(database, ref, "InChI", cleanKey(rootNode.inchi.toString()));
       key = cleanKey(rootNode.inchikey.toString().trim());
       if (key.length() == 27) {
         addAttribute(database, ref, "InChIKey", key);
         addXRef(database, ref, key, inchikeyDS, genesDone, linksDone);
       }
       addAttribute(database, ref, "SMILES", rootNode.smiles.toString());
       addAttribute(database, ref, "BrutoFormula", rootNode.chemical_formula.toString());
       addAttribute(database, ref, "Taxonomy Parent", rootNode.direct_parent.toString());
       addAttribute(database, ref, "Monoisotopic Weight", rootNode.monisotopic_molecular_weight.toString());
  
       // add external identifiers
       // addXRef(database, ref, rootNode.accession.toString(), hmdbDS);
       if (!blacklist.contains(rootid)) {
         addXRef(database, ref, rootNode.cas_registry_number.toString(), casDS, genesDone, linksDone);
         addXRef(database, ref, rootNode.pubchem_compound_id.toString(), pubchemDS, genesDone, linksDone);
         addXRef(database, ref, rootNode.chemspider_id.toString(), chemspiderDS, genesDone, linksDone);
         String chebID = rootNode.chebi_id.toString().trim()
         if (chebID.startsWith("CHEBI:")) {
           boolean isPrimary = !deprChEBIIDs.contains(chebID.substring(6))
           addXRef(database, ref, chebID, chebiDS, genesDone, linksDone, isPrimary);
           addXRef(database, ref, chebID.substring(6), chebiDS, genesDone, linksDone, isPrimary);
         } else if (chebID.length() > 0) {
           boolean isPrimary = !deprChEBIIDs.contains(chebID)
           addXRef(database, ref, chebID, chebiDS, genesDone, linksDone, isPrimary);
           addXRef(database, ref, "CHEBI:" + chebID, chebiDS, genesDone, linksDone, isPrimary);
         }
         String keggID = rootNode.kegg_id.toString();
         if (keggID.length() > 0 && keggID.charAt(0) == 'C') {
           if (!blacklist.contains(keggID)) {
             addXRef(database, ref, keggID, keggDS, genesDone, linksDone);
           } else {
             println "Warn: No external IDs added for: " + keggID
           }
         } else if (keggID.length() > 0 && keggID.charAt(0) == 'D') {
           addXRef(database, ref, keggID, keggDrugDS, genesDone, linksDone);
         }
  //      addXRef(database, ref, rootNode.nugowiki.toString(), nugoDS);
  //      addXRef(database, ref, rootNode.drugbank_id.toString(), drugbankDS);
  //      addXRef(database, ref, rootNode.inchi.toString(), inchiDS);
       } else {
         println "Warn: No external IDs added for: " + rootid
       }
  
       if (error > 0) println "errors: " + error + " (HMDB: ${entry.name})"
       counter++
       if (counter % commitInterval == 0) database.commit()
    }
  }
  unitReport << "  <testcase classname=\"HMDBCreation\" name=\"HMDBInputFound\"/>\n"
} else { // else HMDB does not exist and do nothing
  unitReport << "  <testcase classname=\"HMDBCreation\" name=\"HMDBInputFound\">\n"
  unitReport << "    <failure type=\"FileNotFound\">HMDB input file not found</failure>\n"
  unitReport << "  </testcase>\n"
}

// load the ChEBI content
counter = 0
// load the names
def chebiNames = new File('data/chebi_names.tsv')
chebiNames.eachLine { line,number ->
  if (number == 1) return // skip the first line

  error = 0
  columns = line.split('\t')
  if (columns.length < 5) {
    println "Error (chebi_names.tsv): unexpected line: " + line
    return;
  }
  shortid = columns[1]
  boolean isPrimary = !deprChEBIIDs.contains(shortid)
  rootid = "CHEBI:" + shortid
  name = columns[4]
  // println rootid + " -> " + name
  Xref shortRef = new Xref(shortid, chebiDS, false); // not primary by default
  if (!genesDone.contains(shortRef.toString())) {
    addError = database.addGene(shortRef);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(shortRef,shortRef);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(shortRef.toString())
  }
  Xref ref = new Xref(rootid, chebiDS, isPrimary);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }
  addAttribute(database, ref, "Symbol", name);

  if (error > 0) println "errors: " + error + " (ChEBI: $rootid)"
  counter++
  if (counter % commitInterval == 0) database.commit()
}
unitReport << "  <testcase classname=\"ChEBICreation\" name=\"NamesFound\"/>\n"
// load the mappings
def mappedIDs = new File('data/chebi_database_accession.tsv')
mappedIDs.eachLine { line,number ->
  if (number == 1) return // skip the first line

  columns = line.split('\t')
  rootid = "CHEBI:" + columns[1]
  type = columns[3]
  error = 0
  if (!blacklist.contains(rootid)) {
    id = columns[4]
    println "$rootid -($type)-> $id"
    boolean isPrimary = !deprChEBIIDs.contains(rootid)
    Xref ref = new Xref(rootid, chebiDS, isPrimary);
    if (type == "CAS Registry Number") {
      if (!id.contains(" ") && !id.contains(":") && id.contains("-")) {
        addXRef(database, ref, id, casDS, genesDone, linksDone);
      }
    } else if (type == "KEGG COMPOUND accession") {
      addXRef(database, ref, id, keggDS, genesDone, linksDone);
    } else if (type == "Chemspider accession") {
      addXRef(database, ref, id, chemspiderDS, genesDone, linksDone);
    } else if (type == "Pubchem accession") {
      addXRef(database, ref, id, pubchemDS, genesDone, linksDone);
    } else if (type == "LIPID MAPS class accession") {
      addXRef(database, ref, id, lmDS, genesDone, linksDone);
    } else if (type == "LIPID MAPS instance accession") {
      addXRef(database, ref, id, lmDS, genesDone, linksDone);
    } else if (type == "KNApSAcK accession") {
      addXRef(database, ref, id, knapsackDS, genesDone, linksDone);
    }
  } else {
    println "No external IDs added for: " + rootid
  }
  counter++
  if (error > 0) println "errors: " + error + " (ChEBI: $rootid)"
  if (counter % commitInterval == 0) {
    database.commit()
  }
}
unitReport << "  <testcase classname=\"ChEBICreation\" name=\"AccessionsFound\"/>\n"

//// load the Wikidata content

// CAS registry numbers
counter = 0
error = 0
new File("cas2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], casDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (CAS)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"CASNumbersFound\"/>\n"

// PubChem registry numbers
counter = 0
error = 0
new File("pubchem2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], pubchemDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (PubChem)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"PubChemFound\"/>\n"

// KEGG registry numbers
counter = 0
error = 0
new File("kegg2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  keggID = fields[1]
  if (keggID.charAt(0) == 'C') {
    addXRef(database, ref, keggID, keggDS, genesDone, linksDone);
  } else if (keggID.charAt(0) == 'D') {
    addXRef(database, ref, keggID, keggDrugDS, genesDone, linksDone);
  } else {
    println "unclear KEGG ID ($rootid): " + keggID
  }
  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (KEGG)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"KEGGFound\"/>\n"

// ChemSpider registry numbers
counter = 0
error = 0
new File("cs2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], chemspiderDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (ChemSpider)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"ChemSpiderFound\"/>\n"

// IUPHAR registry numbers
counter = 0
error = 0
new File("gpl2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
 if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
   genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], iupharDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (IUPHAR)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"IUPHARFound\"/>\n"

//// VMH Metabolite identifiers
//counter = 0
//error = 0
//new File("Recon_test.csv").eachLine { line,number ->
//  if (number == 1) return // skip the first line
//  fields = line.split(",")
//  rootid = fields[0].trim() //no substring split needed, since we're mapping on inchiKey (for now)
//  recon = fields[1].trim()
//  Xref ref = new Xref(rootid, inchikeyDS); // add first column to InchiKey mappings.
// if (!genesDone.contains(ref.toString())) {
//    addError = database.addGene(ref);
//    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
//    error += addError
//    linkError = database.addLink(ref,ref);
//    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
//    error += linkError
//   genesDone.add(ref.toString())
//  }

//  // add external identifiers
  
//  addXRef(database, ref, recon, vmhmetaboliteDS, genesDone, linksDone);
  
//  counter++
//  if (counter % commitInterval == 0) {
//    println "Info: errors: " + error + " (VMH Metabolite)"
//    database.commit()
//  }
//}
//unitReport << "  <testcase classname=\"VMHMetaboliteCreation\" name=\"ReconFound\"/>\n" //Need to check if adapted line functions properly.


// ChEMBL Compound registry numbers
counter = 0
error = 0
new File("chembl2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
 if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
   genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], chemblDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (ChEMBL)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"CHEMBLFound\"/>\n"

// Drugbank Compound registry numbers
counter = 0
error = 0
new File("drugbank2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  originalDrugBankID = fields[1]
  longDrugbankID = "DB" + fields[1]

  Xref ref = new Xref(rootid, wikidataDS);
 if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
   genesDone.add(ref.toString())
  }

  // add external identifiers, check if they start with or without "DB" to avoid wrong links.
  if (originalDrugBankID.startsWith("DB")) {
    addXRef(database, ref, fields[1], drugbankDS, genesDone, linksDone);
  }
  else{  
    addXRef(database, ref, longDrugbankID, drugbankDS, genesDone, linksDone);
  }

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (Drugbank)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"DrugbankFound\"/>\n"


// LIPID MAPS registry numbers
counter = 0
error = 0
new File("lm2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], lmDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (LIPIDMAPS)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"LipidMapsFound\"/>\n"

// HMDB registry numbers
counter = 0
error = 0
new File("hmdb2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  hmdbid = fields[1]
  if (hmdbid.length() == 11) {
    hmdbid = "HMDB" + hmdbid.substring(6) // use the pre-16 August 2017 identifier pattern
  }
  addXRef(database, ref, hmdbid, hmdbDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (HMDB)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"HMDBFound\"/>\n"

// EPA CompTox Dashboard numbers
counter = 0
error = 0
new File("comptox2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], dtxDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (EPA CompTox Dashboard)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"EPACompToxFound\"/>\n"

// ChEBI registry numbers
counter = 0
error = 0
new File("chebi2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  shortid = fields[1]
  chebiid = "CHEBI:" + shortid
  Xref chebiRef = new Xref(rootid, chebiDS);
  addXRef(database, ref, shortid, chebiDS, genesDone, linksDone);
  addXRef(database, ref, chebiid, chebiDS, genesDone, linksDone);
  addXRef(database, chebiRef, rootid, wikidataDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (ChEBI)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"ChEBIFound\"/>\n"

// KNApSAcK registry numbers
counter = 0
error = 0
new File("knapsack2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], knapsackDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (ChEBI)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"KNApSAcKFound\"/>\n"

// SwissLipids identifiers
counter = 0
error = 0
new File("swisslipids2wikidata.csv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split(",")
  rootid = fields[0].substring(31)
  Xref ref = new Xref(rootid, wikidataDS);
  if (!genesDone.contains(ref.toString())) {
    addError = database.addGene(ref);
    if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
    error += addError
    linkError = database.addLink(ref,ref);
    if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
    error += linkError
    genesDone.add(ref.toString())
  }

  // add external identifiers
  addXRef(database, ref, fields[1], swisslipidsDS, genesDone, linksDone);

  counter++
  if (counter % commitInterval == 0) {
    println "Info: errors: " + error + " (SwissLipds)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"SwissLipidsFound\"/>\n"

// Wikidata names
counter = 0
error = 0
new File("names2wikidata.tsv").eachLine { line,number ->
  if (number == 1) return // skip the first line

  fields = line.split("\t")
  if (fields.length >= 3) {
    rootid = fields[0].replace("<","").replace(">","").substring(31)
    key = cleanKey(fields[1].trim())
    synonym = fields[2].trim().replace("\"","").replace("@en","")
    Xref ref = new Xref(rootid, wikidataDS);
    if (!genesDone.contains(ref.toString())) {
      addError = database.addGene(ref);
      if (addError != 0) println "Error (addGene): " + database.recentException().getMessage()
      error += addError
      linkError = database.addLink(ref,ref);
      if (linkError != 0) println "Error (addLinkItself): " + database.recentException().getMessage()
      error += linkError
      genesDone.add(ref.toString())
    }
    if (synonym.length() > 0 && !synonym.equals(rootid)) {
      println "Adding synonym: " + synonym
      addAttribute(database, ref, "Symbol", synonym)
      addXRef(database, ref, key, inchikeyDS, genesDone, linksDone);
    } else {
      println "Not adding synonym: " + synonym
    }
    if (key.length() > 0) {
      addAttribute(database, ref, "InChIKey", key);
    }
  }
  counter++
  if (counter % commitInterval == 0) {
    println "errors: " + error + " (label)"
    database.commit()
  }
}
unitReport << "  <testcase classname=\"WikidataCreation\" name=\"NamesFound\"/>\n"
unitReport << "</testsuite>\n"

database.commit();
database.finalize();
